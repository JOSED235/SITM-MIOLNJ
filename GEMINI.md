# SITM-MIO: Instrucciones del Proyecto

Este proyecto es un sistema distribuido de monitoreo y análisis para el SITM-MIO. Utiliza Java 17+, Gradle 7+ y ZeroC Ice 3.7.10.

## Estructura de Compilación y Ejecución

**Importante:** Todas las instrucciones de compilación y ejecución deben realizarse desde la raíz del código fuente:
`C:\Users\Fa1097la\OneDrive - Universidad Icesi\Quinto Semestre\Ingesoft 4\Entrega-SITM-MIO\Entrega-SITM-MIO\3_Implementacion\sitm-mio`

### 1. Compilación General
Para compilar todos los módulos y generar los archivos JAR:
```bash
./gradlew build
```
Esto generará los archivos `.jar` en la carpeta `build/libs/` de cada subproyecto.

### 2. Ejecución del Sistema de Monitoreo (Tiempo Real)
Inicie los componentes en el siguiente orden, cada uno en una terminal nueva parada en la ruta mencionada arriba:

1.  **Data Center (Persistencia y Reportes):**
    ```bash
    java -jar data-center/build/libs/data-center.jar
    ```
2.  **Event Processor (Middleware):**
    ```bash
    java -jar event-processor/build/libs/event-processor.jar
    ```
3.  **Visualizer Client (Interfaz Gráfica):**
    ```bash
    # Nota: Requiere entorno gráfico (JavaFX)
    java -jar visualizer-client/build/libs/visualizer-client.jar
    ```
4.  **Bus Simulator (Simulación de Ingesta):**
    ```bash
    java -jar bus-simulator/build/libs/bus-simulator.jar data/chunck.csv
    ```

### 3. Ejecución de Experimentos de Velocidad (V1, V2, V3)

#### V1 (Monolítica) y V2 (Concurrente)
```bash
java -jar speed-calculator/build/libs/speed-calculator.jar v1 data/lines-241-ActiveGT.csv
java -jar speed-calculator/build/libs/speed-calculator.jar v2 data/lines-241-ActiveGT.csv
```

#### V3 (Distribuida Master-Worker)
1.  **Iniciar Workers (puedes iniciar varios):**
    ```bash
    java -jar speed-worker/build/libs/speed-worker.jar
    ```
2.  **Iniciar Master:**
    ```bash
    java -jar speed-master/build/libs/speed-master.jar data/lines-241-ActiveGT.csv
    ```

## Diagnóstico y Soluciones Comunes

- **Visualizador (Mapa):** Se ha corregido el problema donde el mapa se veía cortado o no cargaba completo. Se ajustó el CSS para asegurar que ocupe el 100% de la ventana y se añadió un `map.invalidateSize()` para forzar el refresco tras la carga.
- **Error de Conexión Ice:** Asegúrese de que el `Data Center` esté corriendo antes que el `Event Processor`, y que este último esté corriendo antes que el `Visualizer` o el `Bus Simulator`.
- **Librerías JavaFX:** Si al ejecutar el visualizador hay errores de módulos, asegúrese de haber corrido `./gradlew build` recientemente para que las dependencias se copien correctamente.

## Convenciones de Desarrollo
- Los contratos se definen en `contracts/src/main/slice/sitm.ice`.
- Al modificar el archivo `.ice`, ejecute `./gradlew :contracts:compileSlice` o simplemente `./gradlew build`.
