# SITM-MIO: Instrucciones del Proyecto

Este proyecto es un sistema distribuido de monitoreo y análisis para el SITM-MIO. Utiliza Java 17+, Gradle 7+ y ZeroC Ice 3.7.10.

## Ubicación de Trabajo
Todas las instrucciones de compilación y ejecución deben realizarse desde la raíz del código fuente:
`C:\Users\Fa1097la\OneDrive - Universidad Icesi\Quinto Semestre\Ingesoft 4\Entrega-SITM-MIO\Entrega-SITM-MIO\3_Implementacion\sitm-mio`

## 1. Compilación
Para compilar todos los módulos:
```bash
./gradlew build
```

## 2. Ejecución del Sistema

He configurado el proyecto para que soporte **dos formas de ejecución**. Elige la que prefieras:

### Opción A: Usando Gradle (Recomendado para desarrollo)
Es la forma más rápida y segura ya que Gradle gestiona todas las librerías automáticamente.

1. **Data Center:** `./gradlew :data-center:run`
2. **Event Processor:** `./gradlew :event-processor:run`
3. **Visualizer:** `./gradlew :visualizer-client:run`
4. **Bus Simulator:** `./gradlew :bus-simulator:run --args="data/chunck.csv"`

### Opción B: Usando archivos JAR (Recomendado para producción)
He corregido la configuración para que `java -jar` funcione perfectamente al incluir todas las dependencias en el manifiesto.

1. **Data Center:** `java -jar data-center/build/libs/data-center.jar`
2. **Event Processor:** `java -jar event-processor/build/libs/event-processor.jar`
3. **Visualizer:** `java -jar visualizer-client/build/libs/visualizer-client.jar`
4. **Bus Simulator:** `java -jar bus-simulator/build/libs/bus-simulator.jar data/chunck.csv`

---

## Solución de Problemas y Mejoras Recientes

### Errores de Librerías (Ice/NoClassDefFoundError)
- **Causa:** Los archivos JAR no incluían las dependencias en su "Class-Path".
- **Solución:** He actualizado el `build.gradle` para que:
  1. El plugin `application` habilite las tareas `run`.
  2. Las tareas `copyLibs` y `copyProjectJars` coloquen todas las dependencias (Ice, Contracts, JavaFX) en la carpeta `build/libs` de cada módulo.
  3. El manifiesto de cada JAR apunte correctamente a estas librerías.

### Visualizador y Mapa (Optimización Total)
- **Mapa Cortado:** Se forzó el redimensionamiento del mapa en 3 tiempos diferentes durante la carga para asegurar que llene el 100% del `WebView`.
- **Lag:** Se desactivaron las animaciones de zoom y fade de Leaflet, y se configuró un buffer de tiles más agresivo para evitar huecos en blanco.
- **JavaFX Runtime:** Se utiliza la clase `SITM.Launcher` para iniciar la aplicación, solucionando el error de "runtime components are missing".

## Convenciones
- Los contratos están en `contracts/src/main/slice/sitm.ice`.
- Tras cambiar el `.ice`, ejecute `./gradlew build`.
