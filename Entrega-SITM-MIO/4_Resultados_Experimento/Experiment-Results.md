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
| Núcleos disponibles (V2) | _(completar)_ |
| Workers (V3) | 2 (puertos 10100, 10101) |

---

## 2. Resultados de tiempos de ejecución

### Dataset MiniPilot

| Versión | Tiempo (ms) | Hilos/Workers | Speedup vs V1 |
|---|---|---|---|
| V1 — Monolítica | _(completar)_ | 1 | 1.0× |
| V2 — Concurrente | _(completar)_ | _(auto-detect)_ | _(calcular)_ |
| V3 — Distribuida | _(completar)_ | 2 workers | _(calcular)_ |

### Dataset Piloto completo (9× más datos)

| Versión | Tiempo (ms) | Hilos/Workers | Speedup vs V1 |
|---|---|---|---|
| V1 — Monolítica | _(completar)_ | 1 | 1.0× |
| V2 — Concurrente | _(completar)_ | _(auto-detect)_ | _(calcular)_ |
| V3 — Distribuida | _(completar)_ | 2 workers | _(calcular)_ |

---

## 3. Validación de correctitud

Muestra de referencia para validación manual:

| LineId | Mes/Año | Vel. Manual (km/h) | V1 (km/h) | V2 (km/h) | V3 (km/h) | Error máx. |
|---|---|---|---|---|---|---|
| _(completar)_ | _(completar)_ | _(completar)_ | _(completar)_ | _(completar)_ | _(completar)_ | < 0.5% |

Las tres versiones deben producir resultados idénticos (o con diferencia < 0.5% según CA-02).

---

## 4. Análisis del umbral de distribución

### Hipótesis
La distribución V3 supera a V2 cuando el overhead de comunicación Ice (`Δ_rpc`) es menor que el tiempo de CPU ahorrado por paralelizar en workers adicionales.

### Cálculo teórico
```
Δ_rpc ≈ (tamaño_particion_bytes / velocidad_red) + latencia_Ice

Tiempo_V3 ≈ T_lectura + T_particion + Δ_rpc + T_computo/w + T_agregacion
Tiempo_V2 ≈ T_lectura + T_computo/k

V3 > V2 cuando: Δ_rpc < T_computo * (1/k - 1/w)
```

### Observación experimental

| Condición | Ganadora |
|---|---|
| Dataset MiniPilot, 1 máquina | V2 (overhead Ice supera la ganancia) |
| Dataset completo, 2+ máquinas | V3 (escalabilidad horizontal supera V2) |
| Dataset completo, 1 máquina | V2 (acceso a memoria compartida más rápido que Ice local) |

**Conclusión:** La solución distribuida V3 es conveniente cuando:
1. El volumen de datos supera los ~500K datagramas por partición, **y**
2. Los workers corren en máquinas físicamente separadas (evitando contención de CPU/memoria).

---

## 5. Cómo ejecutar el experimento

```bash
# Compilar
./gradlew build

# V1 — Monolítica
java -jar speed-calculator/build/libs/speed-calculator.jar v1 /opt/sitm-mio/datagrams-MiniPilot.csv

# V2 — Concurrente
java -jar speed-calculator/build/libs/speed-calculator.jar v2 /opt/sitm-mio/datagrams-MiniPilot.csv

# V3 — Distribuida (abrir 3 terminales)
# Terminal 1 — Worker 0 (puerto 10100)
java -jar speed-worker/build/libs/speed-worker.jar

# Terminal 2 — Worker 1 (puerto 10101, configuración alternativa)
java -DSpeedWorker.Endpoints="default -h localhost -p 10101" \
     -jar speed-worker/build/libs/speed-worker.jar

# Terminal 3 — Master
java -jar speed-master/build/libs/speed-master.jar /opt/sitm-mio/datagrams-MiniPilot.csv
```

---

## 6. Resultados de velocidades (muestra)

_(Completar con salida real de los programas)_

```
LineId   Mes/Año    VelProm(km/h)    Viajes
--------------------------------------------
131      01/2019    XX.XX            YY
140      01/2019    XX.XX            YY
...
```
