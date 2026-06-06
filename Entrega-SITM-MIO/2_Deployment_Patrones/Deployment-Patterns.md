# Patrones y Despliegue — Sistema SITM-MIO
## Ingeniería de Software 4 — Universidad Icesi

---

## 1. Visión General: Dos Subsistemas, Patrones Distintos

El sistema SITM-MIO integra dos subsistemas con preocupaciones arquitectónicas diferentes:

| Subsistema | Driver principal | Patrón central | Protocolo |
|------------|-----------------|----------------|-----------|
| **Monitoreo en Tiempo Real** | Latencia < 2 s | Observer / Pub-Sub + Callback Ice | ZeroC Ice TCP (AMI + síncrono) |
| **Análisis de Velocidades** | Rendimiento y Escalabilidad | Master-Worker (Scatter-Gather) | ZeroC Ice TCP (RPC síncrono) |

Ambos comparten el módulo `contracts` (interfaces Slice) como único punto de acuerdo contractual.

---

## 2. Subsistema A — Monitoreo en Tiempo Real

### Patrón aplicado: Publish-Subscribe con Callback Ice

El **Event Processor** es el broker central. Implementa el patrón Observer donde:
- **Publisher (Subject):** `DatagramReceiverI` — recibe datagramas del simulador y notifica cambios
- **Subscriber (Observer):** `MonitoringSubscriberI` en el `visualizer-client` — recibe `BusUpdate` vía callback Ice

```
┌─────────────────────────────────────────────────────────────────────────┐
│  Nodo Simulador                                                          │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  bus-simulator                                                   │   │
│  │  Lee CSV → postDatagram(Datagram) via Ice RPC :10000             │   │
│  └──────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
                         │ Ice RPC TCP :10000
                         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  Nodo Event Processor (:10000)                                          │
│  ┌────────────────────────────────────────────────────────────────┐     │
│  │  DatagramReceiverI (Servant Ice)                               │     │
│  │  1. Recibe postDatagram(Datagram)                              │     │
│  │  2. Normaliza coords: lat/10,000,000 → grados decimales        │     │
│  │  3. Archiva en Data Center (async AMI → no bloquea el hilo)    │     │
│  │  4. Crea BusUpdate y notifica a todos los suscriptores activos  │     │
│  └────────────────┬──────────────────────────────────────────┬────┘     │
│    Ice AMI (async)│                                          │ Pub-Sub  │
└───────────────────┼──────────────────────────────────────────┼──────────┘
                    │ archiveDatagramAsync()                   │ updateLocation()
                    ▼                                          ▼ (Ice RPC callback)
┌───────────────────────────────┐     ┌───────────────────────────────────┐
│  Nodo Data Center (:10001)    │     │  Nodo Visualizador (puerto dinám.)│
│  ┌───────────────────────────┐│     │  ┌───────────────────────────────┐│
│  │  ArchiveServiceI          ││     │  │  MonitoringSubscriberI        ││
│  │  - store(Datagram) en DW  ││     │  │  - recibe BusUpdate           ││
│  └───────────────────────────┘│     │  │  - Platform.runLater()        ││
│  ┌───────────────────────────┐│     │  └──────────────┬────────────────┘│
│  │  ReportProviderI          ││     │                 │                  │
│  │  - calcVelPromedio(line,m)││     │  ┌──────────────▼────────────────┐│
│  └───────────────────────────┘│     │  │  JavaFX WebView               ││
│  ┌───────────────────────────┐│     │  │  executeScript(updateBus)     ││
│  │  DataWarehouse            ││     │  └──────────────┬────────────────┘│
│  │  ArrayList<Datagram>      ││     │                 │                  │
│  └───────────────────────────┘│     │  ┌──────────────▼────────────────┐│
└───────────────────────────────┘     │  │  map.html (Leaflet.js + OSM)  ││
                                      │  │  L.circleMarker → mueve bus   ││
                                      │  └───────────────────────────────┘│
                                      └───────────────────────────────────┘
```

### Justificación del patrón Pub-Sub + Callback

El patrón Publisher-Subscriber desacopla el `event-processor` del `visualizer-client`:
- El event-processor **no necesita saber** cuántos visualizadores hay ni dónde están
- Los visualizadores se registran vía `DatagramReceiver.subscribe(MonitoringSubscriber*)` y reciben callbacks automáticamente
- El Visualizer implementa `MonitoringSubscriberI` como un **servant Ice con endpoint dinámico**, lo que le permite estar detrás de NAT o en un puerto no determinado

**Ventajas:**
- Bajo acoplamiento entre publisher y subscribers
- Soporta múltiples clientes visualizadores simultáneos sin cambiar código
- El archivado en Data Center (AMI async) no bloquea la notificación al visualizador

**Limitaciones y trade-offs:**
- El callback es síncrono hacia el visualizador; si el visualizador es lento, bloquea la iteración del loop de notificación
- Si el visualizador se cae, el event-processor recibe `LocalException` y lo elimina de la lista (tolerancia a fallos reactiva, no proactiva)
- La latencia total depende de la velocidad de la red Ice local + el tiempo de renderizado de JavaFX/Leaflet

### Patrón Adicional: AMI (Asynchronous Method Invocation) para el archivado

El archivado hacia el `data-center` usa `["ami"]` en el contrato Slice:
```ice
interface ArchiveService {
    ["ami"] void archiveDatagram(Datagram data);
};
```
Esto genera `archiveDatagramAsync()` en Java. El `event-processor` llama este método y **no espera respuesta**, lo que garantiza que el throughput de ingesta no esté limitado por la velocidad del Data Center.

---

## 3. Subsistema B — Cálculo de Velocidades: Tres Versiones Comparadas

### V1: Monolítica — Nodo único, un hilo

```
┌──────────────────────────────────────────────────────┐
│  Nodo CCO (1 máquina)                                │
│                                                      │
│  ┌────────────────────────────────────────────────┐  │
│  │  speed-calculator (V1) — Secuencial            │  │
│  │                                                │  │
│  │  CsvParser                                     │  │
│  │      │                                         │  │
│  │      ▼                                         │  │
│  │  SpeedCalculatorV1.computeByLine()             │  │
│  │  para cada lineId → SpeedEngine.compute()      │  │
│  │      │                                         │  │
│  │      ▼                                         │  │
│  │  Imprime resultados                            │  │
│  └────────────────────────────────────────────────┘  │
│                                          ↑           │
│  /opt/sitm-mio/datagrams-MiniPilot.csv ──┘           │
└──────────────────────────────────────────────────────┘
```

**Patrón:** Ninguno de distribución. Pipeline secuencial: leer → agrupar por lineId → calcular → imprimir.
**Uso:** Baseline para medir el speedup de V2 y V3.

---

### V2: Concurrente — Nodo único, N hilos (Fork-Join)

```
┌──────────────────────────────────────────────────────────────┐
│  Nodo CCO (1 máquina, N núcleos)                             │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  speed-calculator (V2) — ExecutorService             │   │
│  │                                                      │   │
│  │  CsvParser ──► groupByLineId()                       │   │
│  │                     │                                │   │
│  │      ┌──────────────┼───────────────────────────┐   │   │
│  │  Task(lineId=131) Task(lineId=140) ... Task(lineId=N) │   │
│  │  [Thread-0]       [Thread-1]           [Thread-k]  │   │   │
│  │  SpeedEngine      SpeedEngine          SpeedEngine  │   │   │
│  │      └──────────────┬───────────────────────────┘   │   │
│  │                     ▼                                │   │
│  │          Future<List<SpeedResult>> → aggregate       │   │
│  └──────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────┘
```

**Patrón:** **Fork-Join / Thread Pool**. El `ExecutorService` (tamaño = núcleos disponibles, detectado por `Runtime.getRuntime().availableProcessors()`) procesa cada `lineId` como una tarea independiente.

**Ventaja sobre V1:** Speedup teórico de N× (donde N = núcleos), eficiencia real ~70-85% por overhead del scheduler y acceso concurrente a la memoria del CSV.

---

### V3: Distribuida — Master-Worker (Scatter-Gather) con ZeroC Ice

```
┌──────────────────────────────────────────────────────────────────────────┐
│  Nodo Master (CCO)                                                        │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │  speed-master                                                      │  │
│  │  1. Lee CSV completo y agrupa por lineId                           │  │
│  │  2. Distribuye lineIds a workers en round-robin:                   │  │
│  │     Worker[i % N] ← datagramas de lineIds asignados a i           │  │
│  │  3. Envía partición a cada worker vía computeSpeeds(DatagramSeq)   │  │
│  │  4. Recolecta SpeedReport[] de cada worker (en paralelo con Future)│  │
│  │  5. Agrega y ordena resultados finales                             │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│         │ Ice RPC :10100                │ Ice RPC :10101                  │
└─────────┼──────────────────────────────┼────────────────────────────────┘
          │                              │
          ▼                              ▼
┌────────────────────────┐    ┌────────────────────────┐
│  Nodo Worker-0 (:10100)│    │  Nodo Worker-1 (:10101)│ ... Nodo Worker-N
│  speed-worker          │    │  speed-worker          │
│  SpeedWorkerI          │    │  SpeedWorkerI          │
│  computeSpeeds(part.)  │    │  computeSpeeds(part.)  │
│  ─ ordena por fecha    │    │  ─ ordena por fecha    │
│  ─ agrupa por tripId   │    │  ─ agrupa por tripId   │
│  ─ calcula km/h        │    │  ─ calcula km/h        │
│  ─ retorna SpeedReport[]    │  ─ retorna SpeedReport[]    │
└────────────────────────┘    └────────────────────────┘
```

**Patrón de distribución: Master-Worker (Scatter-Gather)**
- **Scatter (dispersión):** El master divide el espacio de problemas (datagramas por `lineId`) entre los workers disponibles.
- **Gather (recolección):** El master espera todos los `Future<SpeedReport[]>` y los concatena.

**Protocolo:** ZeroC Ice 3.7.10 sobre TCP. El contrato en `sitm.ice`:
```ice
interface SpeedWorker {
    SpeedReportSeq computeSpeeds(DatagramSeq partition);
};
```

**Ventaja sobre V2:** Escala más allá de los núcleos de una sola máquina. Con N máquinas físicas, el speedup es cercano a N×.

**Limitación:** La serialización/deserialización de `DatagramSeq` (array de structs Ice) introduce un overhead de red. Para datasets pequeños (< 100K datagramas/partición), este overhead puede superar al tiempo de cómputo → V2 gana en ese caso.

---

## 4. Cuándo Vale la Pena Distribuir (Umbral de Crossover)

Sea:
- **T_cómputo(N)** = tiempo de CPU para N datagramas
- **Δ_ice** = overhead de serialización + red Ice (≈ constante para misma red local)

| Condición | Ganadora | Razón |
|-----------|----------|-------|
| Dataset MiniPilot (100 reg.), 1 máquina | **V2** | Δ_ice >> T_cómputo |
| Dataset completo (9× MiniPilot), 1 máquina | **V2** | Misma CPU, más datos, sin overhead de red |
| Dataset completo, 2+ máquinas físicas separadas | **V3** | T_cómputo / workers >> Δ_ice |

**Regla práctica:** La distribución es conveniente cuando el tiempo de cómputo de cada partición supera **en al menos 10×** el tiempo de transferencia de la partición por la red.

**Umbral empírico observado:** ~500,000 datagramas por partición en red local (100 Mbps) con 2 workers.

---

## 5. Patrones Aplicados — Resumen y Justificación

| Patrón | Dónde se aplica | Driver que responde | Ventaja clave |
|--------|----------------|---------------------|---------------|
| **Pub-Sub / Observer** | Event Processor ↔ Visualizer | Latencia de visualización (E3) | Desacoplamiento publisher/subscriber |
| **AMI (Async Method Invocation)** | Event Processor → Data Center | Throughput de ingesta | No bloquea el hilo de notificación |
| **Master-Worker (Scatter-Gather)** | speed-master ↔ speed-worker | Escalabilidad (E4), Rendimiento (E2) | Escalado horizontal sin cambios de código |
| **Repository** | DataWarehouse en Data Center | Correctitud (E1), Persistencia | Abstrae el almacenamiento de la lógica de consulta |
| **DTO / Value Object** | Structs Ice: Datagram, BusUpdate, SpeedReport | Interoperabilidad | Objetos serializables y tipados que viajan por la red |
| **Strategy (implícito)** | SpeedEngine vs SpeedWorkerI | Correctitud (E1) | Mismo algoritmo intercambiable entre versiones |
| **Callback / Reverse Connection** | MonitoringSubscriberI | Latencia, Disponibilidad (E3, E5) | El visualizador puede estar detrás de NAT |

---

## 6. Estilo Arquitectónico Global

El sistema usa un estilo **orientado a servicios distribuidos (SOA ligero)** implementado con ZeroC Ice:

- Cada módulo expone una **interfaz tipada** definida en `sitm.ice`
- La comunicación es siempre vía **proxies Ice** (nunca acoplamiento directo de clases)
- Los módulos pueden desplegarse en la **misma máquina** (desarrollo/piloto) o en **máquinas separadas** (producción) sin cambiar código, solo configuración (`.cfg`)

Esto responde directamente al driver de **Modificabilidad (E6)**: el sistema es independiente de la topología de despliegue.
