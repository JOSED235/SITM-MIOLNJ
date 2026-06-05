# Documento de Resultados del Experimento
## Sistema SITM-MIO — Cálculo de Velocidad Promedio por Ruta

---

## 1. Configuración del experimento

| Parámetro | Valor |
|---|---|
| Dataset MiniPilot | `datagrams-MiniPilot.csv` |
| Dataset Piloto completo | `datagrams4Pilot.csv` |
| Rutas activas | `lines-241-ActiveGT.csv` |
| JVM | Java 17 |
| Núcleos disponibles (V2) | _(auto-detectado)_ |
| Workers (V3) | 2 (puertos 10100, 10101) |

---

## 2. Resultados de tiempos de ejecución

### Dataset MiniPilot (o `chunck.csv`)

| Versión | Tiempo (ms) | Hilos/Workers | Speedup vs V1 |
|---|---|---|---|
| V1 — Monolítica | _(completar)_ | 1 | 1.0× |
| V2 — Concurrente | _(completar)_ | _(num_cores)_ | _(calcular)_ |
| V3 — Distribuida | _(completar)_ | 2 workers | _(calcular)_ |

### Dataset Piloto completo (9× más datos)

| Versión | Tiempo (ms) | Hilos/Workers | Speedup vs V1 |
|---|---|---|---|
| V1 — Monolítica | _(completar)_ | 1 | 1.0× |
| V2 — Concurrente | _(completar)_ | _(num_cores)_ | _(calcular)_ |
| V3 — Distribuida | _(completar)_ | 2 workers | _(calcular)_ |

---

## 3. Validación de correctitud (Identidad)

Las tres versiones deben producir resultados idénticos para el mismo dataset.

| LineId | Mes/Año | V1 (km/h) | V2 (km/h) | V3 (km/h) | Coincide? |
|---|---|---|---|---|---|
| 1472 | 05/2019 | _(ejemplo: 25.20)_ | _(debe ser igual)_ | _(debe ser igual)_ | Sí |

---

## 4. Análisis del umbral de distribución

### Hipótesis
La distribución V3 supera a V2 cuando el overhead de comunicación Ice es menor que el tiempo de CPU ahorrado por paralelizar en workers adicionales.

**Conclusión:** La solución distribuida V3 es conveniente cuando:
1. El volumen de datos es lo suficientemente grande para que el tiempo de cómputo sea órdenes de magnitud mayor al tiempo de transferencia de red.
2. Los workers corren en máquinas físicamente separadas.

---

## 5. Cómo ejecutar el experimento (Paso a paso)

### Preparación
```bash
# Situarse en la raíz de la implementación
cd 3_Implementacion/sitm-mio

# Compilar
./gradlew build
```

### Ejecución V1 y V2
```bash
# V1 — Monolítica
java -jar speed-calculator/build/libs/speed-calculator.jar v1 data/chunck.csv

# V2 — Concurrente
java -jar speed-calculator/build/libs/speed-calculator.jar v2 data/chunck.csv
```

### Ejecución V3 (Distribuida)
Requiere 3 terminales:

1. **Terminal 1 (Worker 0):**
   ```bash
   java -jar speed-worker/build/libs/speed-worker.jar
   ```

2. **Terminal 2 (Worker 1):**
   ```bash
   java -DSpeedWorker.Endpoints="default -h localhost -p 10101" -jar speed-worker/build/libs/speed-worker.jar
   ```

3. **Terminal 3 (Master):**
   ```bash
   java -jar speed-master/build/libs/speed-master.jar data/chunck.csv
   ```
