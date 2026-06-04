# Deployment con Patrones — Sistema SITM-MIO

## 1. Vista de Deployment — Tres Versiones

### V1: Monolítica (Nodo único)

```
┌─────────────────────────────────────┐
│  Nodo CCO (1 máquina)               │
│                                     │
│  ┌──────────────────────────┐       │
│  │  speed-calculator (V1)   │       │
│  │  - CsvParser             │       │
│  │  - SpeedEngine           │  ←── /opt/sitm-mio/datagrams-MiniPilot.csv
│  │  - SpeedCalculatorV1     │       │
│  └──────────────────────────┘       │
└─────────────────────────────────────┘
```

**Patrón:** Nada. Procesamiento puramente secuencial.

---

### V2: Concurrente (Nodo único, multi-hilo)

```
┌─────────────────────────────────────────────────────┐
│  Nodo CCO (1 máquina, N núcleos)                    │
│                                                     │
│  ┌──────────────────────────────────────────────┐   │
│  │  speed-calculator (V2)                       │   │
│  │                                              │   │
│  │  CsvParser ──► Partition by lineId           │   │
│  │                    │                         │   │
│  │     ┌──────────────┼──────────────────┐      │   │
│  │  Thread-0       Thread-1  ...  Thread-N│     │   │
│  │  [lineIds 0..k] [lineIds k..2k]        │     │   │
│  │     └──────────────┬──────────────────┘      │   │
│  │                    ▼                         │   │
│  │             Aggregate results                │   │
│  └──────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

**Patrón:** Fork-Join / Thread Pool. Cada `lineId` es procesado por un hilo independiente del `ExecutorService`.

---

### V3: Distribuida — Patrón Master-Worker con ZeroC Ice

```
┌─────────────────────────────────────────────────────────────────────┐
│  Nodo Master (CCO)                                                   │
│                                                                      │
│  ┌──────────────────────────────────────────────────┐               │
│  │  speed-master                                     │               │
│  │  1. Lee CSV                                       │               │
│  │  2. Particiona por lineId (round-robin)           │               │
│  │  3. Envía particion[i] → Worker[i % N] via Ice   │               │
│  │  4. Agrega SpeedReportSeq de todos los workers   │               │
│  └──────────────────────────────────────────────────┘               │
│           │ Ice RPC (SpeedWorker.computeSpeeds)                       │
└───────────┼─────────────────────────────────────────────────────────┘
            │
     ┌──────┴──────────────────────────────┐
     │                                     │
     ▼                                     ▼
┌──────────────────────┐         ┌──────────────────────┐
│  Nodo Worker-0       │  ...    │  Nodo Worker-N       │
│  speed-worker        │         │  speed-worker        │
│  puerto 10100        │         │  puerto 101XX        │
│  SpeedWorkerI        │         │  SpeedWorkerI        │
│  ─ Recibe partition  │         │  ─ Recibe partition  │
│  ─ Calcula speeds    │         │  ─ Calcula speeds    │
│  ─ Retorna reports   │         │  ─ Retorna reports   │
└──────────────────────┘         └──────────────────────┘
```

**Patrón de distribución:** **Master-Worker (Scatter-Gather)**
- El master _scatter_ (dispersa) particiones de datagramas a los workers.
- Cada worker procesa su partición independientemente.
- El master _gather_ (recolecta) los `SpeedReportSeq` y los agrega.

**Protocolo:** ZeroC Ice 3.7 (RPC tipado sobre TCP). Contrato definido en `sitm.ice`:
```ice
interface SpeedWorker {
    SpeedReportSeq computeSpeeds(DatagramSeq partition);
};
```

---

## 2. Configuración de Deployment

### Cuándo vale la pena distribuir (Umbral)

Sea:
- T₁ = tiempo V1 con N datagramas
- T₂ = tiempo V2 con k hilos
- T₃ = tiempo V3 con w workers + overhead Ice (Δ)

La distribución V3 supera a V2 cuando:
```
T₁ / w  >  Δ_rpc
```
Es decir, cuando el tiempo de CPU ahorrado por worker supera el overhead de serialización y comunicación Ice.

Para el dataset MiniPilot (pequeño), T₁ es bajo y Δ_rpc puede dominar → V2 gana.
Para el dataset completo (9× más datos), T₁ crece y Δ_rpc se vuelve insignificante → V3 escala mejor.

**Regla práctica observada:** Distribuir vale la pena cuando el dataset tiene más de ~500,000 registros por ruta procesada, o cuando el número de máquinas disponibles supera el número de núcleos de una sola máquina.

---

## 3. Módulos y responsabilidades

| Módulo | Versión | Patrón | Protocolo |
|---|---|---|---|
| `speed-calculator` | V1, V2 | Secuencial / Thread Pool | Local (ninguno) |
| `speed-worker` | V3 | Worker (Servant Ice) | ZeroC Ice / TCP |
| `speed-master` | V3 | Master (Scatter-Gather) | ZeroC Ice / TCP |
| `data-center` | Monitoreo | Repository + Archive | ZeroC Ice / TCP (AMI) |
| `event-processor` | Monitoreo | Pub-Sub | ZeroC Ice / TCP |
| `bus-simulator` | Monitoreo | Cliente Ice | ZeroC Ice / TCP |
| `visualizer-client` | Monitoreo | Subscriber (Callback) | ZeroC Ice / TCP |
