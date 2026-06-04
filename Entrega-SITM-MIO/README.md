# Entrega Final — Sistema SITM-MIO
## Ingeniería de Software 4 — Grupo 4

---

## Estructura de la entrega

```
Entrega-SITM-MIO/
├── 1_Drivers_Arquitectura_QAW/
│   └── QAW-Scenarios.md          ← Escenarios de atributos de calidad
│
├── 2_Deployment_Patrones/
│   └── Deployment-Patterns.md    ← Diagrama de deployment + patrones usados
│
├── 3_Implementacion/
│   └── sitm-mio/                 ← Código fuente completo (Gradle multi-módulo)
│       ├── contracts/            ← Contratos Ice (sitm.ice)
│       ├── speed-calculator/     ← V1 (monolítica) y V2 (concurrente)
│       ├── speed-worker/         ← V3 Nodo Worker (Ice servant)
│       ├── speed-master/         ← V3 Nodo Master (Scatter-Gather)
│       ├── bus-simulator/        ← Simulador de buses (monitoreo en tiempo real)
│       ├── event-processor/      ← Procesador de eventos + Pub-Sub
│       ├── data-center/          ← Almacén + ReportProvider
│       └── visualizer-client/    ← Visualizador JavaFX con mapa
│
└── 4_Resultados_Experimento/
    └── Experiment-Results.md     ← Plantilla con análisis y resultados
```

---

## Cómo compilar y ejecutar

### Requisitos
- Java 17+
- Gradle 7+ (incluido en el wrapper `gradlew`)
- ZeroC Ice 3.7.10 (descargado automáticamente por Gradle)

### Compilar todo
```bash
cd 3_Implementacion/sitm-mio
./gradlew build        # Linux/Mac
gradlew.bat build      # Windows
```

---

## V1 — Monolítica (secuencial)

```bash
java -jar speed-calculator/build/libs/speed-calculator.jar v1 /opt/sitm-mio/datagrams-MiniPilot.csv
```

## V2 — Concurrente (multi-hilo)

```bash
java -jar speed-calculator/build/libs/speed-calculator.jar v2 /opt/sitm-mio/datagrams-MiniPilot.csv
```

## V3 — Distribuida con patrón Master-Worker (ZeroC Ice)

Abrir **3 terminales** desde `3_Implementacion/sitm-mio/`:

**Terminal 1 — Worker 0 (puerto 10100):**
```bash
java -jar speed-worker/build/libs/speed-worker.jar
```

**Terminal 2 — Worker 1 (puerto 10101):**
```bash
java -DSpeedWorker.Endpoints="default -h localhost -p 10101" \
     -jar speed-worker/build/libs/speed-worker.jar
```

**Terminal 3 — Master:**
```bash
java -jar speed-master/build/libs/speed-master.jar /opt/sitm-mio/datagrams-MiniPilot.csv
```

---

## Módulos del sistema de monitoreo en tiempo real

```bash
java -jar data-center/build/libs/data-center.jar
java -jar event-processor/build/libs/event-processor.jar
java -jar visualizer-client/build/libs/visualizer-client.jar
java -jar bus-simulator/build/libs/bus-simulator.jar data/chunck.csv
```
