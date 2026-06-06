# Sistema SITM-MIO — Monitoreo y Análisis Distribuido
## Ingeniería de Software 4 — Universidad Icesi

Sistema distribuido de monitoreo en tiempo real y análisis de rendimiento para el **Sistema Integrado de Transporte Masivo MIO** de Cali. Implementado con **Java 17**, **Gradle 8.6** y **ZeroC Ice 3.7.10**.

---

## Tabla de Contenidos

1. [Requisitos del Sistema](#-requisitos-del-sistema)
2. [Arquitectura](#-arquitectura)
3. [Estructura de Carpetas](#-estructura-de-carpetas)
4. [Compilación](#-compilación)
5. [Ejecución — Análisis de Velocidades (V1, V2, V3)](#-ejecución--análisis-de-velocidades)
6. [Ejecución — Sistema de Monitoreo Completo (Tiempo Real)](#-ejecución--sistema-de-monitoreo-completo)
7. [Descripción de Módulos](#-descripción-de-módulos)
8. [Puertos y Configuración ICE](#-puertos-y-configuración-ice)
9. [Solución de Problemas](#-solución-de-problemas)

---

## Requisitos del Sistema

### Software Obligatorio

| Herramienta | Versión mínima | Descarga |
|-------------|---------------|---------|
| **JDK** | 17 (LTS) | [adoptium.net](https://adoptium.net/) |
| **Gradle** | 8.6 (incluido como wrapper) | No requiere instalación extra |

> **Nota:** ZeroC Ice y JavaFX se descargan automáticamente desde Maven Central durante la compilación. No se requiere instalarlos por separado.

### Verificar instalación de Java

```powershell
java -version
# Debe mostrar: openjdk version "17.x.x" o similar
```

---

## Arquitectura

```
                          ┌─────────────────────────────────────────────┐
                          │          RED ZeroC Ice (localhost)           │
                          │                                              │
  ┌───────────────┐       │  ┌────────────────┐   ┌──────────────────┐ │
  │  bus-simulator│──────►│  │ event-processor│──►│   data-center    │ │
  │  (CSV reader) │       │  │  :10000        │   │   :10001         │ │
  └───────────────┘       │  │  Pub-Sub       │   │  Data Warehouse  │ │
                          │  └───────┬────────┘   └──────────────────┘ │
                          │          │ suscripcion                      │
                          │  ┌───────▼────────┐                        │
                          │  │visualizer-client│                        │
                          │  │  JavaFX + Leaflet│                       │
                          │  └────────────────┘                        │
                          └─────────────────────────────────────────────┘

  ┌────────────────────────────────────────────────────────────────────┐
  │        Análisis de Velocidades (independiente del monitoreo)       │
  │  V1: speed-calculator (secuencial)                                 │
  │  V2: speed-calculator (concurrente — multi-hilo)                   │
  │  V3: speed-master + speed-worker × N (distribuida — Ice RPC)       │
  └────────────────────────────────────────────────────────────────────┘
```

### Flujo de datos (monitoreo en tiempo real)

1. `bus-simulator` lee un archivo CSV y envía datagramas cada 500 ms al `event-processor`.
2. `event-processor` normaliza las coordenadas (divide por 10,000,000), archiva en `data-center` de forma asíncrona y notifica a los suscriptores vía Pub-Sub.
3. `visualizer-client` recibe actualizaciones de posición y las muestra en un mapa Leaflet dentro de una ventana JavaFX.

---

## Estructura de Carpetas

```
Entrega-SITM-MIO/
├── 1_Drivers_Arquitectura_QAW/
│   └── QAW-Scenarios.md          # Escenarios de calidad (rendimiento, escalabilidad)
├── 2_Deployment_Patrones/
│   └── Deployment-Patterns.md    # Diagramas de despliegue V1/V2/V3
├── 3_Implementacion/
│   └── sitm-mio/                 # Raíz del proyecto Gradle multi-módulo
│       ├── build.gradle          # Configuración raíz (Java 17, ZeroC Ice 3.7.10)
│       ├── settings.gradle       # Lista de submódulos
│       ├── gradlew / gradlew.bat # Wrapper de Gradle (NO requiere Gradle instalado)
│       ├── contracts/            # Interfaces Slice (.ice) → código Java generado
│       ├── bus-simulator/        # Lee CSV y envía datagramas
│       ├── event-processor/      # Normaliza, archiva y publica eventos
│       ├── data-center/          # Data Warehouse + reportes históricos
│       ├── visualizer-client/    # UI JavaFX con mapa Leaflet interactivo
│       ├── speed-calculator/     # Análisis de velocidades V1 (secuencial) y V2 (concurrente)
│       ├── speed-worker/         # Nodo esclavo para V3 (Ice RPC)
│       ├── speed-master/         # Nodo maestro para V3 (distribuye carga a workers)
│       ├── data/
│       │   ├── chunck.csv        # Dataset de prueba (100 registros)
│       │   └── lines-241-ActiveGT.csv  # Dataset completo (241 líneas activas)
│       └── docs/                 # Documentación técnica adicional
└── 4_Resultados_Experimento/
    └── Experiment-Results.md     # Métricas de rendimiento comparativas
```

---

## Compilación

> Todos los comandos se ejecutan desde la carpeta `3_Implementacion/sitm-mio/`

```powershell
# En Windows (PowerShell) — usar .\gradlew.bat
cd 3_Implementacion\sitm-mio
.\gradlew.bat clean build
```

```bash
# En Linux / macOS
cd 3_Implementacion/sitm-mio
./gradlew clean build
```

**Primera compilación:** Gradle descargará automáticamente ZeroC Ice, JavaFX y el plugin Ice-Builder (~150 MB). Las compilaciones siguientes son inmediatas.

**Resultado:** Cada módulo genera su JAR en `<modulo>/build/libs/` junto con todas las dependencias copiadas al mismo directorio.

### Compilar un solo módulo

```powershell
# Solo el visualizer (más rápido durante desarrollo)
.\gradlew.bat :visualizer-client:build

# Solo el speed-calculator
.\gradlew.bat :speed-calculator:build
```

---

## Ejecución — Análisis de Velocidades

> Desde la carpeta `3_Implementacion/sitm-mio/` (importante: los JARs buscan el CSV en `data/` relativo al directorio de trabajo)

### V1 — Secuencial (un solo hilo)

```powershell
java -jar speed-calculator/build/libs/speed-calculator.jar v1 data/chunck.csv
```

### V2 — Concurrente (multi-hilo, un hilo por línea de bus)

```powershell
java -jar speed-calculator/build/libs/speed-calculator.jar v2 data/chunck.csv
```

### V3 — Distribuida (Master-Worker con ZeroC Ice)

Se requieren **3 terminales** separadas. Iniciar **en este orden exacto** (los workers deben estar corriendo antes del master).

**Terminal 1 — Worker 0 (puerto 10100):**
```powershell
java -jar speed-worker/build/libs/speed-worker.jar
```

Esperar a ver: `Speed Worker iniciado en default -h localhost -p 10100`

**Terminal 2 — Worker 1 (puerto 10101):**

En PowerShell, los flags `-D` con espacios en el valor deben ir entre comillas envolviendo **toda** la opción:
```powershell
java "-DSpeedWorker.Endpoints=default -h localhost -p 10101" "-DSpeedWorker.Identity=SpeedWorker1" -jar speed-worker/build/libs/speed-worker.jar
```

Esperar a ver: `Speed Worker iniciado en default -h localhost -p 10101`

**Terminal 3 — Master (distribuye carga a los workers):**
```powershell
java -jar speed-master/build/libs/speed-master.jar data/chunck.csv
```

> **Importante (PowerShell):** No usar `\` para continuar líneas — eso es sintaxis bash. En PowerShell se usa el backtick `` ` `` o se escribe todo en una línea.

> Puede agregar más workers creando nuevas entradas `Worker.N` en `speed-master/src/main/resources/speed-master.cfg` antes de compilar.

---

## Ejecución — Sistema de Monitoreo Completo

Se requieren **4 terminales**. **Respetar el orden de arranque.**

> Desde la carpeta `3_Implementacion/sitm-mio/`

### Paso 1 — Data Center

```powershell
java -jar data-center/build/libs/data-center.jar
```

Esperar hasta ver: `Data Center iniciado.`

### Paso 2 — Event Processor

```powershell
java -jar event-processor/build/libs/event-processor.jar
```

Esperar hasta ver: `Event Processor iniciado en puerto 10000.`

### Paso 3 — Visualizer Client (interfaz gráfica)

```powershell
java -jar visualizer-client/build/libs/visualizer-client.jar
```

Se abre una ventana con el mapa de Cali. Esperar hasta ver en consola: `Conectado y suscrito al Event Processor.`

### Paso 4 — Bus Simulator

```powershell
java -jar bus-simulator/build/libs/bus-simulator.jar data/chunck.csv
```

Los buses aparecerán como puntos azules en el mapa actualizándose cada 500 ms.

---

## Descripción de Módulos

### `contracts`
Define todas las interfaces del sistema en el lenguaje Slice de ZeroC Ice (`sitm.ice`). El plugin Gradle Ice-Builder genera automáticamente el código Java durante la compilación. Interfaces principales:

- **`DatagramReceiver`** — recibe datagramas del simulador y permite suscripción de visualizadores
- **`MonitoringSubscriber`** — callback para el visualizador (recibe `BusUpdate`)
- **`ArchiveService`** — persiste datagramas en el Data Center (asíncrono con `["ami"]`)
- **`ReportProvider`** — consulta velocidades históricas por línea/mes/año
- **`SpeedWorker`** — calcula velocidades para una partición de datagramas (V3)

### `bus-simulator`
Lee un CSV de datagramas y los envía al `event-processor` vía Ice RPC. Formato esperado del CSV: `eventType,registerDate,stopId,odometer,latitude,longitude,taskId,lineId,tripId,unknown1,datagramDate,busId`.  
Incluye un `PathResolver` que busca el archivo primero en la ruta exacta, luego en `/opt/sitm-mio/` y finalmente en `data/`.

### `event-processor`
Nodo central del patrón Pub-Sub. Al recibir un datagrama:
1. Lo archiva en el Data Center de forma **asíncrona** (no bloquea el flujo principal).
2. Convierte las coordenadas enteras a grados decimales (÷ 10,000,000).
3. Notifica a todos los visualizadores suscritos.

### `data-center`
Almacén en memoria (Data Warehouse) con cálculo de velocidades promedio por línea/mes/año. El cálculo usa **Distancia Total / Tiempo Total** por viaje, con filtro de valores atípicos (> 120 km/h descartados).

### `visualizer-client`
Aplicación JavaFX que embebe un `WebView` con Leaflet.js mostrando el mapa de Cali (OpenStreetMap). Los buses se representan como círculos azules con popup. El mapa se ajusta automáticamente al tamaño de la ventana mediante llamadas escalonadas a `invalidateSize()`.

### `speed-calculator`
Dispatcher para V1 y V2. Acepta `v1` o `v2` como primer argumento:
- **V1:** Procesamiento secuencial en un solo hilo.
- **V2:** Procesamiento paralelo, un hilo por línea de bus usando `ExecutorService`.

### `speed-worker` / `speed-master`
Implementación V3 del patrón Master-Worker sobre Ice RPC:
- El **master** lee el CSV, particiona los datagramas por línea en round-robin entre workers y agrega resultados en paralelo.
- Cada **worker** implementa `SpeedWorkerI` y calcula velocidades para su partición de forma independiente.

---

## Puertos y Configuración ICE

| Módulo | Puerto | Identificador ICE |
|--------|--------|-------------------|
| `event-processor` | 10000 | `DatagramReceiver` |
| `data-center` | 10001 | `ArchiveService`, `ReportProvider` |
| `speed-worker` (instancia 0) | 10100 | `SpeedWorker` |
| `speed-worker` (instancia 1) | 10101 | `SpeedWorker1` |
| `visualizer-client` (callback) | dinámico | `VisualizerCallback` |

Los archivos `.cfg` de cada módulo se encuentran en `src/main/resources/` y se incluyen automáticamente en el classpath del JAR.

---

## Solución de Problemas

### Error: `No se pudo conectar con el Event Processor`
Verificar que el `event-processor` esté corriendo **antes** de iniciar el simulador o el visualizador. Confirmar que el puerto 10000 no esté en uso:

```bash
# Windows
netstat -ano | findstr :10000

# Linux/macOS
lsof -i :10000
```

### Error: `No se encontro el archivo map.html en resources`
El JAR no incluye `map.html`. Recompilar con `./gradlew :visualizer-client:build`. Verificar que `src/main/resources/map.html` exista.

### El mapa aparece en blanco o con tiles dispersos
Este problema ocurre si el `WebView` de JavaFX reporta dimensiones incorrectas durante la inicialización. El `map.html` incluido ya tiene la corrección (CSS `100%` en lugar de dimensiones calculadas por JS). Si persiste, redimensionar la ventana manualmente fuerza la actualización.

### Error de compilación: `Could not resolve com.zeroc:ice:3.7.10`
Sin conexión a Internet durante la primera compilación. Verificar conectividad. Las compilaciones posteriores usan el caché local de Gradle (~/.gradle/caches).

### Error en Windows: archivos bloqueados durante `clean build`
Cerrar todas las ventanas de Java en ejecución antes de recompilar:

```powershell
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force
.\gradlew.bat clean build
```

### V3: El master muestra `No hay workers disponibles`
Los workers deben estar corriendo **antes** de lanzar el master. Verificar que los puertos 10100 y 10101 estén escuchando.

### CSV no encontrado al ejecutar los JARs
Ejecutar siempre desde la carpeta `3_Implementacion/sitm-mio/` (no desde dentro de `speed-calculator/build/libs/`). Los JARs buscan `data/chunck.csv` relativo al directorio de trabajo.

---

## Datos de Prueba

| Archivo | Registros | Descripción |
|---------|-----------|-------------|
| `data/chunck.csv` | ~100 | Dataset reducido para pruebas rápidas |
| `data/lines-241-ActiveGT.csv` | ~miles | Dataset completo con 241 líneas activas |

---

## Documentación Adicional

| Documento | Descripción |
|-----------|-------------|
| `1_Drivers_Arquitectura_QAW/QAW-Scenarios.md` | Escenarios de atributos de calidad (rendimiento, escalabilidad, modificabilidad) |
| `2_Deployment_Patrones/Deployment-Patterns.md` | Diagramas de despliegue para V1, V2 y V3 |
| `3_Implementacion/sitm-mio/docs/ARCHITECTURE_PLAN.md` | Diagrama de componentes y decisiones de diseño |
| `3_Implementacion/sitm-mio/docs/Experiment-Results.md` | Metodología y resultados de los experimentos de rendimiento |
| `4_Resultados_Experimento/Experiment-Results.md` | Resultados consolidados y análisis comparativo |
