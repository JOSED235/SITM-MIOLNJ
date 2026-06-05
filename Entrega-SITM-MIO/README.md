# Entrega Final — Sistema SITM-MIO
## Ingeniería de Software 4 — Universidad Icesi

Este proyecto implementa un sistema distribuido de monitoreo y análisis de rendimiento para el Sistema Integrado de Transporte Masivo (SITM-MIO), utilizando **Java 17**, **Gradle** y **ZeroC Ice**.

---

## 🚀 Cambios y Mejoras Recientes (Optimización Total)

Se han realizado las siguientes mejoras críticas para garantizar la exactitud y robustez del sistema:

1.  **Precisión en el Cálculo de Velocidad:** Se estandarizó la lógica de cálculo en todos los módulos (V1, V2, V3 y Data Center) utilizando el método de **Distancia Total / Tiempo Total** por viaje. Ahora el sistema ordena cronológicamente los datagramas antes del cálculo, eliminando errores por desorden en los datos.
2.  **Portabilidad Multiplataforma (Path Fallback):** Se implementó un `PathResolver` que permite al sistema buscar los archivos CSV automáticamente. Si no encuentra la ruta `/opt/sitm-mio/`, buscará en la carpeta local `data/`. Esto garantiza que el sistema funcione en Windows y Linux sin cambios de código.
3.  **Robustez del Build:** Se ajustó la configuración de Gradle para asegurar que todas las librerías (Ice, Contratos, JavaFX) se empaqueten correctamente en el `Class-Path` de los archivos JAR.
4.  **Documentación de Experimentos:** Se completó el archivo `Experiment-Results.md` con guías detalladas para recolectar métricas de desempeño.

---

## 📋 Requisitos del Proyecto

### Funcionales
- **Análisis Histórico:** Calcular velocidades promedio por ruta por mes.
- **Monitoreo en Tiempo Real:** Visualizar la ubicación de los buses en un mapa mientras se simula la operación.
- **Persistencia:** Almacenar todos los datagramas recibidos en un centro de datos central.

### Arquitectónicos
- **V1 (Monolítica):** Implementación secuencial básica.
- **V2 (Concurrente):** Uso de multi-threading para acelerar el procesamiento en una sola máquina.
- **V3 (Distribuida):** Implementación usando el patrón Master-Worker con ZeroC Ice para escalabilidad horizontal.

---

## 📂 Estructura de Carpetas

### 1. Drivers Arquitectónicos (`1_Drivers_Arquitectura_QAW`)
Contiene los escenarios QAW y los drivers que guiaron el diseño del sistema.

### 2. Diseño y Despliegue (`2_Deployment_Patrones`)
Documentación sobre los patrones de diseño aplicados y el diagrama de despliegue.

### 3. Implementación (`3_Implementacion/sitm-mio`)
El núcleo del código fuente, organizado en submódulos de Gradle:
- **`contracts`**: Definición de interfaces Slice (ZeroC Ice).
- **`bus-simulator`**: Lector de CSV que envía datagramas en tiempo real.
- **`event-processor`**: Nodo intermedio que normaliza coordenadas y publica eventos (Pub-Sub).
- **`data-center`**: Almacén central (Data Warehouse) y proveedor de reportes históricos.
- **`visualizer-client`**: Aplicación JavaFX con mapa interactivo (Leaflet).
- **`speed-calculator`**: Ejecutable para las versiones **V1** y **V2**.
- **`speed-worker`**: Nodo esclavo para la versión **V3**.
- **`speed-master`**: Nodo maestro que distribuye carga a los workers (**V3**).
- **`data`**: Carpeta con archivos CSV de prueba (`chunck.csv`, `lines-241-ActiveGT.csv`).

### 4. Resultados (`4_Resultados_Experimento`)
Resultados detallados de las pruebas de carga y comparativas de rendimiento.

---

## 🛠️ Guía de Compilación

Desde la carpeta `3_Implementacion/sitm-mio`:

```bash
# Compilar y empaquetar todos los módulos
./gradlew clean build
```
*Nota: Si el comando falla por archivos bloqueados en Windows, asegúrate de cerrar procesos de Java previos.*

---

## 🧪 Pruebas de Funcionamiento (Paso a Paso)

### A. Experimentos de Análisis de Velocidad (V1, V2, V3)

1.  **V1 (Secuencial):**
    ```bash
    java -jar speed-calculator/build/libs/speed-calculator.jar v1 data/chunck.csv
    ```
2.  **V2 (Concurrente):**
    ```bash
    java -jar speed-calculator/build/libs/speed-calculator.jar v2 data/chunck.csv
    ```
3.  **V3 (Distribuida):**
    - Terminal 1: `java -jar speed-worker/build/libs/speed-worker.jar`
    - Terminal 2: `java -DSpeedWorker.Endpoints="default -h localhost -p 10101" -jar speed-worker/build/libs/speed-worker.jar`
    - Terminal 3: `java -jar speed-master/build/libs/speed-master.jar data/chunck.csv`

### B. Sistema de Monitoreo Completo (Tiempo Real)

Ejecutar en este orden exacto (una terminal por comando):

1.  **Data Center:** `java -jar data-center/build/libs/data-center.jar`
2.  **Event Processor:** `java -jar event-processor/build/libs/event-processor.jar`
3.  **Visualizer Client:** `java -jar visualizer-client/build/libs/visualizer-client.jar`
4.  **Bus Simulator:** `java -jar bus-simulator/build/libs/bus-simulator.jar data/chunck.csv`

---

## 📊 Validación de Calidad
Para sustentar técnicamente los resultados, consulta el archivo `3_Implementacion/sitm-mio/docs/Experiment-Results.md`, donde se detallan las métricas de **Escalabilidad** y **Correctitud** obtenidas durante las pruebas.
