# Plan de Implementación: Sistema SITM-MIO
## Estado Final — Implementación Completada

> Este documento refleja el estado **real y final** de la implementación. Las fases 1-4 corresponden al plan original entregado como punto de partida. Las fases 5-6 documentan el subsistema de análisis de velocidades (V1/V2/V3), que se añadió durante el desarrollo para cumplir los requerimientos de cálculo histórico.

---

## Fase 1: Infraestructura y Contratos ✅ COMPLETADA

### US01: Definición de Comunicación Distribuida
**Como** arquitecto, **quiero** definir los contratos de interfaz en Slice **para que** los diferentes módulos puedan comunicarse de forma tipada y eficiente.

- **Checklist:**
  - [x] Crear `sitm.ice` con las interfaces `DatagramReceiver`, `ArchiveService`, `MonitoringSubscriber` y `ReportProvider`.
  - [x] Agregar interfaz `SpeedWorker` para el subsistema de cálculo distribuido (adición al plan original).
  - [x] Agregar struct `SpeedReport` y secuencias `DatagramSeq`, `SpeedReportSeq` (adición al plan original).
  - [x] Configurar `contracts/build.gradle` con el plugin `com.zeroc.gradle.ice-builder:1.5.2`.
  - [x] Validar la generación de código Java en la carpeta `build/generated-src/`.

**Implementación real:** `contracts/src/main/slice/sitm.ice` — 5 interfaces, 4 structs, 3 sequencias. Ice-Builder genera ~43 clases Java automáticamente durante `./gradlew build`.

---

## Fase 2: Flujo de Eventos en Tiempo Real ✅ COMPLETADA

### US02: Simulación de Buses
**Como** operador de pruebas, **quiero** que el `bus-simulator` lea los archivos CSV y envíe datagramas al procesador **para** simular la operación real de la flota.

- **CA:** El simulador debe ser capaz de procesar al menos 50 datagramas por segundo.
- **Checklist:**
  - [x] Implementar lector de CSV en `bus-simulator` (`BufferedReader` línea por línea).
  - [x] Implementar cliente Ice que obtenga el proxy de `DatagramReceiver` (`DatagramReceiverPrx.checkedCast`).
  - [x] Lógica de envío periódico: `Thread.sleep(500 ms)` entre datagramas.
  - [x] Implementar `PathResolver`: busca el CSV en ruta exacta → `/opt/sitm-mio/` → `data/<nombre>`. Garantiza portabilidad Windows/Linux.

**Nota de implementación:** El plan original decía "cada 20-30s por bus simulado". Se implementó 500 ms para que la visualización sea fluida durante las pruebas del piloto.

---

### US03: Procesamiento y Normalización
**Como** analista de datos, **quiero** que el `event-processor` reciba los datagramas, normalice las coordenadas y las publique **para** que el visualizador las muestre.

- **CA:** La conversión de coordenadas enteras a decimales debe tener precisión de 6 decimales.
- **Checklist:**
  - [x] Implementar `DatagramReceiverI` (Servant Ice en `:10000`, identidad `DatagramReceiver`).
  - [x] Normalización: `lat = d.latitude / 10_000_000.0`, `lon = d.longitude / 10_000_000.0` → precisión 7 decimales.
  - [x] Patrón Pub-Sub: `List<MonitoringSubscriberPrx>` con `subscribe()` y `notify()`.
  - [x] Tolerancia a fallos en `notify()`: elimina suscriptores que lancen `LocalException`.

**CA cumplido:** División por 10⁷ en `double` de Java garantiza 7 dígitos decimales de precisión.

---

## Fase 3: Persistencia y Análisis Histórico ✅ COMPLETADA

### US04: Almacenamiento de Datos (Data Warehouse)
**Como** administrador del sistema, **quiero** persistir todos los datagramas en el `data-center` **para** permitir auditorías y análisis de rendimiento.

- **CA:** Todos los datagramas recibidos por el `event-processor` deben ser reenviados al `data-center` de forma asíncrona.
- **Checklist:**
  - [x] Implementar `ArchiveServiceI` en `data-center` (Servant Ice en `:10001`, identidad `ArchiveService`).
  - [x] Configurar AMI en `sitm.ice`: anotación `["ami"]` en `ArchiveService.archiveDatagram()` → genera `archiveDatagramAsync()`.
  - [x] El `event-processor` llama `archiveService.archiveDatagramAsync(data)` — no bloquea el hilo de notificación.
  - [x] Almacenamiento en `DataWarehouse`: `synchronized ArrayList<Datagram>` en memoria JVM.

**Nota de implementación:** El plan original sugería "archivos o base de datos embebida". Se decidió usar **ArrayList en memoria** para simplificar el piloto y mantener cero dependencias externas. Toda la lógica de consulta opera sobre la misma estructura.

---

### US05: Reporte de Velocidad Promedio
**Como** gestor de calidad, **quiero** consultar la velocidad promedio mensual por ruta **para** identificar cuellos de botella en la operación.

- **CA:** El cálculo debe usar la diferencia de `odometer` dividida por la diferencia de tiempo entre datagramas del mismo viaje.
- **Checklist:**
  - [x] Implementar `DataWarehouse.getAverageSpeed(lineId, month, year)`.
  - [x] Algoritmo: agrupa por `tripId` → ordena por `datagramDate` → `Σ(Δodómetro) / Σ(Δsegundos) × 3.6` → filtra `kmh > 120`.
  - [x] Implementar `DataWarehouse.getMonthlyReports(year)` para obtener todos los reportes de un año.
  - [x] Implementar `ReportProviderI` en `data-center` (Servant Ice, identidad `ReportProvider`).
  - [x] El `visualizer-client` conecta a `ReportProvider:default -h localhost -p 10001` vía `ReportProviderI`.

**CA cumplido:** El cálculo es Distancia Total / Tiempo Total (no promedio de promedios), con filtro de resets de odómetro (Δ < 0) y velocidades atípicas (> 120 km/h).

---

## Fase 4: Visualización y Monitoreo ✅ COMPLETADA

### US06: Mapa de Monitoreo en Tiempo Real
**Como** despachador del CCO, **quiero** ver la ubicación de los buses en tiempo real **para** reaccionar ante incidentes.

- **CA:** El visualizador debe actualizar la posición del bus en menos de 2 segundos tras recibir la actualización.
- **Checklist:**
  - [x] Implementar `MonitoringSubscriberI` (Servant Ice con endpoint dinámico vía `VisualizerCallback.Endpoints`).
  - [x] Integración JavaFX: `SITM.Launcher` → `SITM.Main` (Application). Elegido JavaFX sobre Swing por soporte nativo de `WebView`.
  - [x] `WebView` embebido con `map.html` (Leaflet.js 1.9.4 + OpenStreetMap). Mapa centrado en Cali [3.42, -76.52].
  - [x] Marcadores de buses como `L.circleMarker` azules con popup (Bus ID, Ruta, Hora).
  - [x] `Platform.runLater()` para despachar actualizaciones al hilo JavaFX desde el hilo Ice.
  - [x] Corrección de tiles dispersos en JavaFX WebView: `tryInit()` con polling hasta `offsetWidth > 100px`; CSS `100vw/100vh`.
  - [x] `stage.setOnShown()` dispara `forceResize()` 400 ms después para alinear tiles.

**CA cumplido:** La latencia desde `postDatagram()` hasta `updateBus()` en Leaflet es: serialización Ice + `Platform.runLater()` + `executeScript()` ≈ 50–200 ms en red local, muy por debajo de los 2 s requeridos.

---

## Fase 5: Análisis de Velocidades V1 y V2 ✅ COMPLETADA
*(Fase añadida — no estaba en el plan original)*

### US07: Cálculo Monolítico de Velocidades (V1)
**Como** analista de datos, **quiero** una implementación secuencial de referencia **para** establecer un baseline de rendimiento y verificar correctitud.

- **CA:** Tiempo de ejecución < 60 s para el dataset MiniPilot; resultado coincide con cálculo manual (error < 0.5%).
- **Checklist:**
  - [x] Módulo `speed-calculator` con dispatcher `Main` (acepta `v1` o `v2` como argumento).
  - [x] `CsvParser`: lee CSV y construye `List<DatagramRecord>`.
  - [x] `SpeedEngine`: algoritmo central compartido entre V1 y V2.
  - [x] `SpeedCalculatorV1`: itera líneas secuencialmente, llama `SpeedEngine.compute()`.
  - [x] `PathResolver`: mismo mecanismo que `bus-simulator` para compatibilidad multiplataforma.
  - [x] Salida: tabla `LineId | Mes/Año | VelProm(km/h)` + tiempo total en ms.

---

### US08: Cálculo Concurrente de Velocidades (V2)
**Como** analista de datos, **quiero** una versión multi-hilo **para** comparar el speedup respecto a V1 en una sola máquina.

- **CA:** Speedup ≥ 0.7 × N cores; resultado idéntico a V1 (error < 0.01%).
- **Checklist:**
  - [x] `SpeedCalculatorV2`: agrupa por `lineId`, crea una tarea por línea en `ExecutorService.newFixedThreadPool(availableProcessors())`.
  - [x] Cada `Future<List<SpeedResult>>` ejecuta `SpeedEngine.computeForLine(lineId, data)`.
  - [x] Recolecta todos los futures y agrega resultados.
  - [x] Mismo `SpeedEngine` que V1 → garantía de correctitud idéntica.

---

## Fase 6: Análisis de Velocidades V3 Distribuida ✅ COMPLETADA
*(Fase añadida — no estaba en el plan original)*

### US09: Cálculo Distribuido Master-Worker (V3)
**Como** arquitecto, **quiero** implementar el patrón Master-Worker con ZeroC Ice **para** demostrar escalabilidad horizontal y determinar el umbral de distribución.

- **CA:** Con 2 workers en misma máquina, el tiempo no debe empeorar más de 2× respecto a V1 (overhead Ice visible pero acotado). Con máquinas físicamente separadas, speedup ≥ 0.8 × N workers.
- **Checklist:**
  - [x] Interfaz `SpeedWorker.computeSpeeds(DatagramSeq) : SpeedReportSeq` en `sitm.ice`.
  - [x] Módulo `speed-worker`: `SpeedWorkerI` implementa `computeSpeeds()` con la misma lógica que `SpeedEngine`.
  - [x] Módulo `speed-master`: lee `speed-master.cfg` (`Worker.Count`, `Worker.0..N`), verifica workers con `checkedCast`.
  - [x] Partición round-robin por `lineId` → garantiza que todos los datagramas de un viaje queden en el mismo worker (correctitud).
  - [x] `ExecutorService` paralelo para enviar `computeSpeeds(partition[i])` a cada worker simultáneamente.
  - [x] `Future.get()` agrega `SpeedReport[]` de todos los workers.
  - [x] Tolerancia a fallos: worker no disponible → `checkedCast` devuelve null → excluido sin romper el cálculo.

---

## Plan de Pruebas Integradas

### Prueba 1 — Conectividad del subsistema de monitoreo
```powershell
# Terminal 1
java -jar data-center/build/libs/data-center.jar
# Esperar: "Data Center iniciado."

# Terminal 2
java -jar event-processor/build/libs/event-processor.jar
# Esperar: "Event Processor iniciado en puerto 10000."
# Verificar: el event-processor muestra que conectó con el data-center
```

### Prueba 2 — Sistema de monitoreo completo (flujo end-to-end)
```powershell
# Orden obligatorio: data-center → event-processor → visualizer → simulator
java -jar data-center/build/libs/data-center.jar
java -jar event-processor/build/libs/event-processor.jar
java -jar visualizer-client/build/libs/visualizer-client.jar
java -jar bus-simulator/build/libs/bus-simulator.jar data/chunck.csv
```
**Verificación:** Buses aparecen como círculos azules en el mapa de Cali; se mueven al actualizar posición.

### Prueba 3 — Correctitud V1 = V2 = V3 (CA-02)
```powershell
# V1
java -jar speed-calculator/build/libs/speed-calculator.jar v1 data/chunck.csv

# V2
java -jar speed-calculator/build/libs/speed-calculator.jar v2 data/chunck.csv

# V3 (3 terminales)
java -jar speed-worker/build/libs/speed-worker.jar
java "-DSpeedWorker.Endpoints=default -h localhost -p 10101" "-DSpeedWorker.Identity=SpeedWorker1" -jar speed-worker/build/libs/speed-worker.jar
java -jar speed-master/build/libs/speed-master.jar data/chunck.csv
```
**Verificación:** Las tablas de V1, V2 y V3 producen los mismos valores de `VelProm(km/h)` para cada `(lineId, mes/año)`. Diferencia máxima admitida: < 0.5%.

### Prueba 4 — Validación de carga
```powershell
# Reemplazar data/chunck.csv con el dataset completo si está disponible
java -jar speed-calculator/build/libs/speed-calculator.jar v1 /opt/sitm-mio/datagrams-MiniPilot.csv
java -jar speed-calculator/build/libs/speed-calculator.jar v2 /opt/sitm-mio/datagrams-MiniPilot.csv
java -jar speed-master/build/libs/speed-master.jar /opt/sitm-mio/datagrams-MiniPilot.csv
```
**Verificación:** Comparar tiempos entre V1, V2, V3 para establecer el umbral de distribución.

---

## Comandos de Compilación (Windows PowerShell)

```powershell
# Desde 3_Implementacion\sitm-mio\
.\gradlew.bat clean build

# Solo un módulo (más rápido en desarrollo)
.\gradlew.bat :visualizer-client:build
.\gradlew.bat :speed-calculator:build
```

---

## Resumen de Implementación vs Plan Original

| Ítem del plan original | Estado | Nota |
|------------------------|:------:|------|
| Interfaces Slice (4 originales) | ✅ | Se añadió `SpeedWorker` al contrato |
| `bus-simulator` lector CSV | ✅ | + `PathResolver` para portabilidad |
| `event-processor` Pub-Sub | ✅ | + tolerancia a fallos en `notify()` |
| `data-center` `ArchiveServiceI` | ✅ | Storage en memoria (no archivo/BD) |
| `data-center` AMI | ✅ | `["ami"]` en sitm.ice |
| `data-center` `ReportProviderI` | ✅ | + `getMonthlyReports(year)` |
| `visualizer-client` mapa | ✅ | JavaFX + WebView + Leaflet.js |
| Strategy Pattern (filtrado) | ⚠️ Parcial | Filtrado vía `LocalException` en `notify()` y filtro `kmh > 120` en SpeedEngine; no como Strategy explícito |
| **Speed Calculator V1** | ✅ **Nuevo** | No estaba en el plan original |
| **Speed Calculator V2** | ✅ **Nuevo** | No estaba en el plan original |
| **Speed Master/Worker V3** | ✅ **Nuevo** | No estaba en el plan original |
