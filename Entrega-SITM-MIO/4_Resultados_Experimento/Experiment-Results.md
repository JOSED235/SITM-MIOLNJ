# Documento de Resultados del Experimento
## Sistema SITM-MIO — Validación de Drivers Arquitectónicos
### Driver validado: Rendimiento (E2), Escalabilidad (E4), Correctitud (E1)

---

## 1. Configuración del Experimento

| Parámetro | Valor |
|---|---|
| Dataset MiniPilot | `data/chunck.csv` (referencia local) / `datagrams-MiniPilot.csv` (piloto real) |
| Dataset Piloto completo | `datagrams4Pilot.csv` (9× más datos que MiniPilot) |
| Rutas activas de referencia | `lines-241-ActiveGT.csv` |
| JVM | Java 17 (OpenJDK) |
| Núcleos disponibles (V2) | _(detectado automáticamente con `availableProcessors()`)_ |
| Workers V3 | 2 (Worker-0: `:10100`, Worker-1: `:10101`) |
| Máquina de prueba | _(anotar: CPU, núcleos, RAM, OS)_ |

---

## 2. Cómo Ejecutar el Experimento (PowerShell — Windows)

> Ejecutar desde: `3_Implementacion\sitm-mio\`

### Compilar antes de medir

```powershell
.\gradlew.bat clean build
```

### V1 — Monolítica (1 hilo)

```powershell
java -jar speed-calculator/build/libs/speed-calculator.jar v1 data/chunck.csv
```

Para el dataset completo (si está disponible en `/opt/sitm-mio/`):
```powershell
java -jar speed-calculator/build/libs/speed-calculator.jar v1 /opt/sitm-mio/datagrams-MiniPilot.csv
```

### V2 — Concurrente (N hilos, detectados automáticamente)

```powershell
java -jar speed-calculator/build/libs/speed-calculator.jar v2 data/chunck.csv
```

### V3 — Distribuida (3 terminales PowerShell, en este orden)

**Terminal 1 — Worker 0 (puerto 10100):**
```powershell
java -jar speed-worker/build/libs/speed-worker.jar
```

**Terminal 2 — Worker 1 (puerto 10101):**
```powershell
java "-DSpeedWorker.Endpoints=default -h localhost -p 10101" "-DSpeedWorker.Identity=SpeedWorker1" -jar speed-worker/build/libs/speed-worker.jar
```

**Terminal 3 — Master (esperar a que los dos workers estén activos):**
```powershell
java -jar speed-master/build/libs/speed-master.jar data/chunck.csv
```

> **Nota:** En PowerShell, las opciones `-D` con espacios en el valor deben entrar como `"-DKey=valor con espacios"` (comillas envuelven **toda** la opción).

---

## 3. Resultados de Tiempos de Ejecución

### Dataset MiniPilot / `chunck.csv`

| Versión | Tiempo (ms) | Hilos/Workers | Speedup vs V1 | Observación |
|---|---|---|---|---|
| V1 — Monolítica | _(completar)_ | 1 | 1.0× | Baseline |
| V2 — Concurrente | _(completar)_ | _(anotar: N cores)_ | _(T_V1 / T_V2)_ | Speedup esperado: 0.7–0.85 × N |
| V3 — Distribuida | _(completar)_ | 2 workers | _(T_V1 / T_V3)_ | Puede ser > V1 si overhead Ice > T_cómputo |

### Dataset Piloto completo (`datagrams4Pilot.csv` — 9× más datos)

| Versión | Tiempo (ms) | Hilos/Workers | Speedup vs V1 | Observación |
|---|---|---|---|---|
| V1 — Monolítica | _(completar)_ | 1 | 1.0× | Baseline |
| V2 — Concurrente | _(completar)_ | _(N cores)_ | _(calcular)_ | Speedup esperado similar al MiniPilot |
| V3 — Distribuida | _(completar)_ | 2 workers | _(calcular)_ | Esperar speedup > V2 con este volumen |

---

## 4. Validación de Correctitud (Driver E1 — error < 0.5%)

Las tres versiones deben producir resultados idénticos para el mismo dataset. Registrar una muestra de 5 rutas:

| LineId | Mes/Año | V1 (km/h) | V2 (km/h) | V3 (km/h) | Dif. V1-V2 | Dif. V1-V3 | Pasa? |
|---|---|---|---|---|---|---|---|
| _(completar)_ | _(completar)_ | _(completar)_ | _(completar)_ | _(completar)_ | _(< 0.5%)_ | _(< 0.5%)_ | Sí/No |

**Criterio de aceptación (CA-02):** Diferencia entre versiones < 0.5% en todos los registros.

---

## 5. Análisis del Umbral de Distribución (Driver E4)

### Marco teórico

```
Tiempo V3 ≈ T_lectura + T_particion + Δ_ice + T_computo/w + T_agregacion

Donde:
  Δ_ice = overhead de serialización DatagramSeq + latencia de red
  T_computo/w = tiempo de cómputo dividido entre w workers

V3 supera a V2 cuando:
  Δ_ice < T_computo × (1/k_v2 - 1/w_v3)
  
  Donde k_v2 = núcleos disponibles en V2
```

### Resultados experimentales esperados

| Condición | Ganadora esperada | Razón |
|---|---|---|
| Dataset MiniPilot, misma máquina | **V2** | Δ_ice > T_cómputo |
| Dataset completo, misma máquina | **V2** | T_computo crece pero la CPU es la misma |
| Dataset completo, 2+ máquinas físicas | **V3** | T_computo/w >> Δ_ice por red |

### Conclusión

_(Completar tras el experimento)_

La distribución V3 es conveniente cuando:
1. El volumen de datos supera los ~____ datagramas por partición, **y**
2. Los workers corren en máquinas físicamente separadas.

Para el dataset MiniPilot en una sola máquina: V__ es la opción óptima porque _(justificación)_.

---

## 6. Muestra de Salida del Programa

### Salida esperada de V1/V2 (`speed-calculator.jar`):

```
=== V1 MONOLITICA ===
Registros leidos: 100
Lineas activas encontradas: X

LineId   Mes/Año    VelProm(km/h)
--------------------------------------
131      01/2019    XX.XX
140      01/2019    XX.XX
...
--------------------------------------
Total combinaciones ruta-mes: N

Tiempo V1 (monolitica): XXX ms
```

### Salida esperada de V3 (`speed-master.jar`):

```
=== V3 DISTRIBUIDA (Master-Worker) ===
Workers activos: 2
Archivo: data/chunck.csv
Lineas activas encontradas: X
Total datagramas: Y
Enviando ZZZ datagramas al Worker 0
Enviando ZZZ datagramas al Worker 1

LineId   Mes/Año    VelProm(km/h)
--------------------------------------
131      01/2019    XX.XX
...
--------------------------------------
Total combinaciones ruta-mes: N

Tiempo V3 (distribuida, 2 workers): XXX ms
```

_(Pegar aquí la salida real del experimento)_

---

## 7. Validación del Subsistema de Monitoreo (Driver E3 — Latencia < 2s)

Para validar el criterio de latencia de la visualización:

1. Iniciar `data-center`, `event-processor`, `visualizer-client`, `bus-simulator` en ese orden.
2. Observar en la consola del `event-processor` el timestamp de `Procesado Datagrama - Bus: X Lat: Y Lon: Z`.
3. Observar cuándo aparece el marcador del bus en el mapa.
4. Diferencia < 2 s → criterio cumplido.

| Bus ID | Timestamp event-processor | Timestamp aparición en mapa | Latencia | Cumple < 2s? |
|--------|---------------------------|------------------------------|----------|--------------|
| _(completar)_ | _(completar)_ | _(completar)_ | _(ms)_ | Sí/No |
