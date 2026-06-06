# Plan de Arquitectura: Sistema de Monitoreo y Análisis SITM-MIO
## Vista Completa — 8 Módulos, 2 Subsistemas

---

## 1. Diagrama de Componentes (Vista Lógica)

```plantuml
@startuml
skinparam componentStyle rectangle
skinparam backgroundColor #FEFEFE

package "Contratos ICE (sitm.ice)" {
    interface DatagramReceiver
    interface ArchiveService
    interface MonitoringSubscriber
    interface ReportProvider
    interface SpeedWorker
}

package "Subsistema A — Monitoreo Tiempo Real" {
    [bus-simulator] as BS
    [event-processor\nDatagramReceiverI\nPub-Sub Broker] as EP
    [data-center\nArchiveServiceI\nReportProviderI\nDataWarehouse] as DC
    [visualizer-client\nMonitoringSubscriberI\nJavaFX + Leaflet.js] as VC
}

package "Subsistema B — Análisis de Velocidades" {
    [speed-calculator (V1)\nSecuencial - 1 hilo] as V1
    [speed-calculator (V2)\nConcurrente - ExecutorService] as V2
    [speed-master\nMaster - Scatter-Gather] as SM
    [speed-worker × N\nWorker - Ice RPC] as SW
}

BS --> DatagramReceiver : postDatagram(Datagram)
DatagramReceiver <|.. EP
EP --> ArchiveService : archiveDatagramAsync() [AMI]
ArchiveService <|.. DC
EP --> MonitoringSubscriber : updateLocation(BusUpdate)
MonitoringSubscriber <|.. VC
VC --> ReportProvider : getAverageSpeed(lineId, month, year)
ReportProvider <|.. DC

SM --> SpeedWorker : computeSpeeds(DatagramSeq)
SpeedWorker <|.. SW

note right of EP
  Normaliza: lat/10,000,000
  Filtra: LocalException → elimina suscriptor
end note

note right of SM
  Partición round-robin por lineId
  ExecutorService para N workers en paralelo
end note
@enduml
```

---

## 2. Responsabilidades de los 8 Módulos

### `contracts` — Contratos Slice
- **Rol:** Módulo de definición de interfaces; no tiene lógica de negocio.
- **Artefacto:** `sitm.ice` → generado por Ice-Builder a código Java.
- **Interfaces:** `DatagramReceiver`, `ArchiveService`, `MonitoringSubscriber`, `ReportProvider`, `SpeedWorker`
- **Structs:** `Datagram`, `BusUpdate`, `Location`, `SpeedReport`, secuencias `BusUpdateSeq`, `DatagramSeq`, `SpeedReportSeq`

### `bus-simulator` — Productor de Eventos
- **Rol:** Cliente Ice que simula la flota de buses leyendo un CSV.
- **Patrón:** Cliente Ice (proxy `DatagramReceiverPrx`).
- **Comportamiento:** Lee cada línea del CSV, construye un `Datagram`, llama `postDatagram()` y espera 500 ms. Incluye `PathResolver` para buscar el CSV en rutas relativas o `/opt/sitm-mio/`.

### `event-processor` — Broker Pub-Sub Central
- **Rol:** Servidor Ice en `:10000`. Nodo central de procesamiento y distribución.
- **Patrón:** **Observer / Pub-Sub** con **AMI** para el archivado.
- **Responsabilidades:**
  1. Recibe `postDatagram(Datagram)` del simulador
  2. Normaliza coordenadas: `lat / 10,000,000.0` → grados decimales
  3. Archiva en Data Center de forma **asíncrona** (`archiveDatagramAsync`) para no bloquear el flujo
  4. Notifica a suscriptores registrados con `updateLocation(BusUpdate)`
  5. Gestión de fallos: elimina suscriptores que lancen `LocalException`

### `data-center` — Almacén y Proveedor de Reportes
- **Rol:** Servidor Ice en `:10001`. Persiste datagramas y calcula velocidades históricas.
- **Patrón:** **Repository** (`DataWarehouse`) + **Servant Ice** (`ArchiveServiceI`, `ReportProviderI`)
- **Algoritmo de velocidad:** Para cada `(lineId, month, year)`: agrupa por `tripId`, ordena por `datagramDate`, calcula `Σ(Δodómetro) / Σ(Δsegundos) × 3.6`, filtra `kmh > 120`.

### `visualizer-client` — Interfaz Gráfica de Monitoreo
- **Rol:** Aplicación JavaFX. Suscriptor Ice con endpoint dinámico.
- **Patrón:** **Subscriber (Callback Ice)** + **MVC** (Model: markers{}, View: Leaflet.js, Controller: MonitoringSubscriberI)
- **Flujo:**
  1. `Launcher` inicia JavaFX con `WebView` cargando `map.html` (Leaflet.js + OpenStreetMap)
  2. `initIce()` conecta con event-processor y llama `subscribe(MonitoringSubscriberPrx)`
  3. Cada `updateLocation(BusUpdate)` despacha a JavaFX UI thread vía `Platform.runLater()`
  4. Ejecuta `updateBus(busId, lat, lng, lineId, time)` en `map.html` para mover el marcador

### `speed-calculator` — Análisis V1 y V2
- **Rol:** Ejecutable standalone (no necesita red ni Ice en ejecución).
- **Patrón V1:** Secuencial puro.
- **Patrón V2:** **Fork-Join / Thread Pool** con `ExecutorService.newFixedThreadPool(availableProcessors())`. Una tarea por `lineId`.

### `speed-worker` — Nodo Worker V3
- **Rol:** Servidor Ice en puerto configurable (default `:10100`).
- **Patrón:** **Worker (Servant Ice)** en el patrón Master-Worker.
- **Implementa:** `SpeedWorkerI.computeSpeeds(DatagramSeq)` — misma lógica que `SpeedEngine`.

### `speed-master` — Coordinador V3
- **Rol:** Cliente Ice que coordina la distribución y recolección.
- **Patrón:** **Master (Scatter-Gather)**.
- **Flujo:** Lee workers desde `speed-master.cfg` → lee CSV → particiona por `lineId` en round-robin → envía particiones con `ExecutorService` en paralelo → agrega `SpeedReport[]` de todos los workers.

---

## 3. Flujo de Datos — Subsistema de Monitoreo

```
Tiempo real (cada 500 ms en simulación):

bus-simulator ──[Ice RPC TCP :10000]──► event-processor
                                              │
                               ┌──────────────┼──────────────────┐
                               │ (async AMI)  │ (sync notify)    │
                               ▼              ▼                  │
                          data-center    MonitoringSubscriberI   │
                         (DataWarehouse)   (visualizer-client)   │
                                                │                 │
                                    Platform.runLater()           │
                                                │                 │
                                     JavaFX executeScript()       │
                                                │                 │
                                       updateBus() en Leaflet.js ─┘

Consulta histórica (bajo demanda):

visualizer-client ──[Ice RPC TCP :10001]──► data-center
                                              (ReportProviderI)
                                              (DataWarehouse.getAverageSpeed)
```

---

## 4. Flujo de Datos — Subsistema de Velocidades V3

```
speed-master arranca:
  1. Lee speed-master.cfg: Worker.Count=2, Worker.0=:10100, Worker.1=:10101
  2. checkedCast a cada worker → descarta no disponibles
  3. Lee CSV y agrupa datagramas por lineId
  4. Round-robin: Worker[0] ← {lineId=131, 140, ...}, Worker[1] ← {lineId=147, ...}
  5. ExecutorService: envía en paralelo computeSpeeds(particion[i]) a Worker[i]
  6. Future.get() recolecta SpeedReport[] de cada worker
  7. Agrega + ordena + imprime tabla final
```

---

## 5. Decisiones Arquitectónicas Clave

| Decisión | Alternativa Rechazada | Razón |
|---|---|---|
| ZeroC Ice para RPC | REST/HTTP | Ice provee tipado estático, serialización binaria eficiente y soporte nativo para callbacks (AMI, Pub-Sub) |
| AMI para archivado | Llamada síncrona al Data Center | La llamada síncrona bloquea el hilo del event-processor ~5-10ms por datagrama; AMI mantiene el throughput de ingesta |
| Pub-Sub con endpoint dinámico | Polling del visualizador | El polling requeriría una API de consulta continua en el event-processor y agregaría latencia de sondeo |
| CSS `100vw/100vh` + polling en Leaflet | JS `window.innerWidth/Height` | JavaFX WebView reporta dimensiones 0×0 en el primer ciclo de layout; el polling espera a tener dimensiones reales |
| Configuración en `.cfg` | Hardcoded en código | Permite cambiar topología de workers sin recompilar (driver de Modificabilidad) |
| `checkedCast` antes de distribuir | `uncheckedCast` | `checkedCast` verifica que el worker esté activo antes de intentar distribuir; evita `ConnectionRefusedException` tardía |
| Almacenamiento en memoria (ArrayList) | Archivos o base de datos embebida | Cero dependencias externas para el piloto; toda la lógica de consulta corre sobre la misma estructura JVM |
| `SpeedEngine` compartido entre V1/V2/SpeedWorkerI | Implementaciones independientes | Un único punto de verdad para el algoritmo garantiza que V1, V2 y V3 producen resultados idénticos |

---

## 6. Evolución desde el Plan Original (4 módulos → 8 módulos)

### Plan de partida entregado

El plan original describía un sistema de **4 módulos** con flujo lineal:

```
Bus Simulator → Event Processor → Data Center → Visualizer Client
```

Patrones mencionados en el plan original:
- **Observer/Pub-Sub** para el flujo de eventos
- **DTO** para los structs Ice (`Datagram`, `BusUpdate`)
- **Repository** para el almacenamiento en Data Center
- **Strategy** para "filtrado configurable" (mencionado como posibilidad)

### Cambios realizados en la implementación

| Elemento del plan original | Estado | Detalle del cambio |
|---|:---:|---|
| 4 interfaces Slice en `sitm.ice` | ✅ Completado + expandido | Se añadió `SpeedWorker` + structs `SpeedReport`, secuencias `DatagramSeq`/`SpeedReportSeq` |
| `bus-simulator` cliente Ice | ✅ Completado + mejorado | Se añadió `PathResolver` para portabilidad Windows/Linux; delay 500 ms (plan decía 20-30 s) |
| `event-processor` Observer/Pub-Sub | ✅ Completado + corregido | Se eliminó `Thread.sleep(2000)` del bloque `synchronized` que bloqueaba todo el sistema; tolerancia a fallos con `LocalException` |
| `data-center` ArchiveServiceI + AMI | ✅ Completado | Anotación `["ami"]` en Slice genera `archiveDatagramAsync()` |
| `data-center` Repository (DataWarehouse) | ✅ Completado (en memoria) | El plan sugería "archivos o BD embebida"; se implementó como `ArrayList<Datagram>` en memoria JVM |
| `data-center` ReportProviderI | ✅ Completado + extendido | Algoritmo Σ(Δodómetro) / Σ(Δsegundos) × 3.6 con filtros; se añadió `getMonthlyReports(year)` |
| `visualizer-client` mapa | ✅ Completado (tecnología específica) | El plan no especificaba tecnología; se eligió JavaFX 17 + WebView + Leaflet.js 1.9.4 + OpenStreetMap |
| Strategy Pattern (filtrado) | ⚠️ Parcial | El filtrado se hace implícitamente: `LocalException` elimina suscriptores caídos; `kmh > 120` filtra atípicos. No se implementó como clase Strategy explícita, pero `SpeedEngine` cumple el rol de algoritmo intercambiable |
| **Subsistema B completo (V1/V2/V3)** | ✅ **Añadido** | El plan original no contemplaba análisis de velocidades. Se añadieron 4 módulos: `speed-calculator`, `speed-worker`, `speed-master` + interfaz `SpeedWorker` en contratos |

### Por qué se añadió el Subsistema B

El plan original cubría únicamente el monitoreo en tiempo real. Durante el desarrollo se identificó que los **drivers de rendimiento y escalabilidad (E2, E4)** requerían una demostración experimental con tres versiones comparables (baseline secuencial → concurrente → distribuida). El Subsistema B responde directamente a ese requerimiento y valida cuantitativamente el valor de la arquitectura distribuida.

### Diagrama de evolución

```
PLAN ORIGINAL (4 módulos)          IMPLEMENTACIÓN FINAL (8 módulos)
─────────────────────────          ────────────────────────────────

contracts (4 interfaces)     →     contracts (5 interfaces + SpeedWorker,
                                               structs SpeedReport,
                                               DatagramSeq, SpeedReportSeq)

bus-simulator                →     bus-simulator (+ PathResolver)

event-processor              →     event-processor (sin sleep en synchronized,
                                               tolerancia a fallos mejorada)

data-center                  →     data-center (en memoria, no BD/archivo)

visualizer-client            →     visualizer-client (JavaFX + WebView
                                               + Leaflet.js + polling fix)

[no existía]                 →     speed-calculator (V1 secuencial + V2 concurrente)

[no existía]                 →     speed-worker (Servant Ice V3)

[no existía]                 →     speed-master (Coordinador V3 Scatter-Gather)
```
