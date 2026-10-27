# Documento de Resultados del Experimento
## Sistema SITM-MIO — Validación de Drivers Arquitectónicos
### Drivers validados: Correctitud (E1), Rendimiento (E2), Escalabilidad (E4)

> Mediciones del 27 de octubre de 2026. Las tablas con `chunck.csv` usan los datos del repositorio; las de volumen usan **datasets sintéticos** con el mismo formato (ver sección 1), porque los archivos del piloto (`/opt/sitm-mio/`) no estaban disponibles en la máquina de prueba. Si se corre con los datos reales, solo hay que repetir los comandos de la sección 2 y reemplazar las cifras.

---

## 1. Configuración del Experimento

| Parámetro | Valor |
|---|---|
| Dataset local (prueba) | `data/chunck.csv` (100 registros, subconjunto local) |
| Dataset "MiniPilot" sintético | 300.000 registros, 5.012 viajes, 111 rutas activas, ene–jun 2019 (28 MB) |
| Dataset "Piloto completo" sintético | 2.700.000 registros (9× el anterior), 44.973 viajes (258 MB) |
| Cómo se generaron | Mismo formato de 12 columnas de `chunck.csv`. Cada viaje es un bus de una ruta de `lines-241-ActiveGT.csv` que reporta cada 20–40 s con el odómetro avanzando a 10–35 km/h. Generador con semilla fija: `tools/GenDatagrams.java` (semillas 241 y 2419). Ej.: `java tools/GenDatagrams.java data/lines-241-ActiveGT.csv mini.csv 300000 241`. |
| Rutas activas de referencia | `data/lines-241-ActiveGT.csv` (111 rutas, PLANVERSIONID=241) |
| JVM | Java 23 compilando con `--release 17` |
| Máquina | Intel Core i5-8250U (4 núcleos / 8 hilos), 19,9 GB RAM, Windows 11 Home |
| Hilos V2 | 8 (`Runtime.getRuntime().availableProcessors()`) |
| Workers V3 | 2, en la misma máquina (Worker-0: `:10100`, Worker-1: `:10101`) |
| Repeticiones | 3 por versión y dataset; se reporta la **mediana** (las tres medidas entre paréntesis) |
| Heap | `-Xmx4g` para V1, V2 y master; `-Xmx3g` para cada worker |

> **Nota PathResolver:** Si los archivos no están en `/opt/sitm-mio/`, el sistema busca automáticamente en `data/<nombre_archivo>`. No se requiere cambio de código ni configuración.

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

Para el dataset MiniPilot (en servidor con `/opt/sitm-mio/`):
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
java -jar speed-worker/build/libs/speed-worker.jar --Ice.MessageSizeMax=0
```

**Terminal 2 — Worker 1 (puerto 10101):**
```powershell
java -jar speed-worker/build/libs/speed-worker.jar 10101 --Ice.MessageSizeMax=0
```

**Terminal 3 — Master (esperar a que los dos workers muestren "Speed Worker iniciado"):**
```powershell
java -jar speed-master/build/libs/speed-master.jar data/chunck.csv --Ice.MessageSizeMax=0
```

> **Por qué así (corregido el 27-oct-2026):**
> 1. Ice no lee propiedades de la JVM (`-DSpeedWorker.Endpoints=...`), solo las que llegan como `--Propiedad=valor` o en el `.cfg`. Con el comando anterior el Worker 1 ignoraba el puerto 10101, intentaba abrir el 10100 (ya ocupado) y se caía con `SocketException`, y el master no encontraba a nadie en `:10101`. El worker ya acepta el puerto como primer argumento. La identidad debe quedar en `SpeedWorker`, que es la que espera `speed-master.cfg`.
> 2. `--Ice.MessageSizeMax=0` quita el límite de 1 MB por mensaje de Ice. Sin él, con 300.000 registros cada worker recibe ~150.000 datagramas en una sola llamada, supera el límite y cierra la conexión (`SocketException: Connection reset by peer` en el master).

---

## 3. Resultados de Tiempos de Ejecución

El tiempo que imprime cada versión incluye leer el CSV, filtrar por rutas activas, calcular y (en V3) enviar a los workers y recibir resultados.

### Dataset local `chunck.csv` (100 registros — validación de correctitud y comportamiento)

| Versión | Tiempo (ms) | Hilos/Workers | Speedup vs V1 | Observación |
|---|---|---|---|---|
| V1 — Monolítica | **52** | 1 | 1,00× | Pipeline secuencial |
| V2 — Concurrente | **65** | 8 hilos | 0,80× | 52 rutas con datos pero solo 1 viaje con 2+ puntos: el pool cuesta más que el cálculo |
| V3 — Distribuida | **69** | 2 workers | 0,75× | Conexión Ice + serialización > cálculo |

### Dataset "MiniPilot" sintético (300.000 registros)

| Versión | Tiempo (ms) | Hilos/Workers | Speedup vs V1 | Observación |
|---|---|---|---|---|
| V1 — Monolítica | **1.899** (1.882 / 1.899 / 2.087) | 1 | 1,00× | Baseline |
| V2 — Concurrente | **1.296** (1.272 / 1.296 / 1.382) | 8 hilos | **1,47×** | La más rápida |
| V3 — Distribuida | **1.411** (1.188 / 1.411 / 2.043) | 2 workers | 1,35× | La primera corrida es más lenta (workers "en frío") |

### Dataset "Piloto completo" sintético (2.700.000 registros, 9×)

| Versión | Tiempo (ms) | Hilos/Workers | Speedup vs V1 | Observación |
|---|---|---|---|---|
| V1 — Monolítica | **11.452** (11.250 / 11.452 / 11.616) | 1 | 1,00× | Baseline |
| V2 — Concurrente | **7.280** (7.200 / 7.280 / 7.544) | 8 hilos | **1,57×** | La más rápida |
| V3 — Distribuida | **9.990** (8.878 / 9.990 / 13.102) | 2 workers | 1,15× | Mucha variación: tres JVM compiten por la misma CPU y RAM |

### Cuánto pesa la lectura del CSV

Medido aparte con `CsvParser.parse()` (3 corridas, misma máquina):

| Dataset | Lectura del CSV | % del tiempo de V1 |
|---|---|---|
| 300.000 registros | 313 – 535 ms | ~20–28 % |
| 2.700.000 registros | 2.791 – 3.120 ms | ~25–27 % |

Esa parte es secuencial en las tres versiones (V2 y el master de V3 leen todo el archivo en un solo hilo antes de repartir).

---

## 4. Validación de Correctitud (Driver E1 — error < 0.5%)

Se compararon **todas** las combinaciones ruta-mes de las tres versiones, no solo una muestra:

| Dataset | Combinaciones V1 / V2 / V3 | Faltantes | Diferencia máxima V1-V2 | Diferencia máxima V1-V3 | Pasa? |
|---|---|---|---|---|---|
| `chunck.csv` | 1 / 1 / 1 | 0 | 0,00 % | 0,00 % | ✅ Sí |
| MiniPilot sintético | 666 / 666 / 666 | 0 | 0,00 % | 0,00 % | ✅ Sí |
| Piloto completo sintético | 666 / 666 / 666 | 0 | 0,00 % | 0,00 % | ✅ Sí |

Muestra de 5 rutas (Piloto completo sintético):

| LineId | Mes/Año | V1 (km/h) | V2 (km/h) | V3 (km/h) | Dif. V1-V2 | Dif. V1-V3 | Pasa? |
|---|---|---|---|---|---|---|---|
| 131 | 01/2019 | 22,64 | 22,64 | 22,64 | 0,00 % | 0,00 % | ✅ Sí |
| 2243 | 05/2019 | 22,08 | 22,08 | 22,08 | 0,00 % | 0,00 % | ✅ Sí |
| 306 | 05/2019 | 24,30 | 24,30 | 24,30 | 0,00 % | 0,00 % | ✅ Sí |
| 3351 | 05/2019 | 22,89 | 22,89 | 22,89 | 0,00 % | 0,00 % | ✅ Sí |
| 552 | 01/2019 | 22,17 | 22,17 | 22,17 | 0,00 % | 0,00 % | ✅ Sí |

Con `chunck.csv` las tres versiones dan **1472 · 05/2019 · 25,20 km/h**.

**Criterio de aceptación (CA-02) cumplido:** Las tres versiones usan el mismo algoritmo (`SpeedEngine`/`SpeedWorkerI`): agrupación por `tripId` → ordenamiento por `datagramDate` → `Σ(Δodómetro) / Σ(Δsegundos) × 3.6` → filtro Δ≤0 y kmh>120.

> **Ojo:** `lines-241-ActiveGT.csv` incluye la ruta `-1, TESTGT1` (una ruta de prueba). El sistema la trata como activa y la reporta en los resultados (6 de las 666 combinaciones). Si no debe contar, hay que filtrarla.

---

## 5. Análisis del Umbral de Distribución (Driver E4)

### Marco teórico

```
Tiempo V3 ≈ T_lectura + T_particion + Δ_ice + T_computo/w + T_agregacion

Donde:
  Δ_ice     = overhead de serialización DatagramSeq + latencia de red
  T_computo = tiempo de cómputo total
  w         = número de workers

V3 supera a V2 cuando:
  Δ_ice < T_computo × (1/k_v2 - 1/w_v3)
  
  Donde k_v2 = núcleos disponibles en V2
```

### Resultados observados

| Condición | Resultado | Por qué |
|---|---|---|
| `chunck.csv` (100 reg.), 1 máquina | **V1 gana** (52 ms) | Cualquier paralelismo (pool o Ice) cuesta más que calcular un viaje |
| 300.000 reg., 1 máquina | **V2 gana** (1,47×) | El pool ya se amortiza; V3 queda cerca (1,35×) |
| 2.700.000 reg., 1 máquina | **V2 gana** (1,57×), V3 se aleja (1,15×) | Los 2 workers y el master comparten los mismos 4 núcleos y la RAM; serializar ~1,35 M datagramas por worker pesa más |
| Workers en máquinas separadas | **No medido** | Con una sola máquina no se puede probar; es el caso donde V3 debería ganar |

### Por qué V2 no llega a 8×

1. **Lectura secuencial (~27 %):** con Amdahl, si el 27 % no se paraleliza, el techo con 8 hilos es `1 / (0,27 + 0,73/8) ≈ 2,8×`.
2. **El filtrado y el `groupingBy` por ruta** también corren en un solo hilo antes de repartir el trabajo.
3. **4 núcleos físicos:** los 8 hilos son hyperthreading, no 8 núcleos reales.
4. **Ordenamiento costoso:** en `SpeedEngine.tripSpeed()` el comparador llama a `parsedDate()` en cada comparación, así que la fecha se vuelve a convertir de texto muchas veces por viaje. Convertirla una sola vez al leer el CSV debería bajar el tiempo de cómputo en las tres versiones.

### Conclusión del umbral

- En **una sola máquina, V2 es la opción óptima** a partir de unos cientos de miles de registros. V3 no le gana porque los workers no traen CPU nueva: compiten con el master por los mismos núcleos.
- **V3 tiene sentido** cuando los workers corren en **máquinas físicamente separadas** y el volumen por partición es grande. Con los datos de esta prueba, cada worker procesó ~1,35 M datagramas; ese es el orden de magnitud a partir del cual vale la pena medir con 2+ máquinas.
- Para `chunck.csv`, **V1 es óptima**: el dataset es mínimo.

---

## 6. Salida Real del Programa

### V1 — `chunck.csv`

```
=== V1 MONOLITICA ===
Archivo: data/chunck.csv
Rutas activas (lines-241-ActiveGT.csv): 111
Registros leidos: 100
Registros de rutas activas: 100

LineId   Mes/Año   VelProm(km/h)    Viajes
----------------------------------------------
1472     05/2019    25,20            1
----------------------------------------------
Total combinaciones ruta-mes: 1

Tiempo V1 (monolitica): 52 ms
```

### V2 — `chunck.csv`

```
=== V2 CONCURRENTE ===
Archivo: data/chunck.csv
Rutas activas (lines-241-ActiveGT.csv): 111
Hilos disponibles: 8
Registros leidos: 100
Rutas con datos en el dataset: 52

LineId   Mes/Año   VelProm(km/h)    Viajes
----------------------------------------------
1472     05/2019    25,20            1
----------------------------------------------
Total combinaciones ruta-mes: 1

Tiempo V2 (concurrente, 8 hilos): 65 ms
```

### V3 — `chunck.csv`

```
=== V3 DISTRIBUIDA (Master-Worker) ===
Workers activos: 2
Archivo: data/chunck.csv
Rutas activas (lines-241-ActiveGT.csv): 111
Rutas con datos en el dataset: 52
Total datagramas: 100
Enviando 51 datagramas al Worker 0
Enviando 49 datagramas al Worker 1

LineId   Mes/Año    VelProm(km/h)
--------------------------------------
1472     05/2019    25,20
--------------------------------------
Total combinaciones ruta-mes: 1

Tiempo V3 (distribuida, 2 workers): 69 ms
```

### V3 — Piloto completo sintético (resumen)

```
=== V3 DISTRIBUIDA (Master-Worker) ===
Workers activos: 2
Total combinaciones ruta-mes: 666
Tiempo V3 (distribuida, 2 workers): 9990 ms
```

---

## 7. Validación del Subsistema de Monitoreo (Driver E3 — Latencia < 2 s)

> **Estado: no medido todavía.** La prueba necesita el `visualizer-client` (JavaFX + mapa Leaflet) en pantalla para tomar el momento en que aparece el marcador. Lo de abajo es una **estimación teórica**, no una medición.

### Procedimiento

```
Orden obligatorio:
1. java -jar data-center/build/libs/data-center.jar
2. java -jar event-processor/build/libs/event-processor.jar
3. java -jar visualizer-client/build/libs/visualizer-client.jar
4. java -jar bus-simulator/build/libs/bus-simulator.jar data/chunck.csv
```

### Análisis teórico de latencia (E2E)

```
Latencia total = T_Ice_RPC(postDatagram) 
               + T_normalización(÷10,000,000)
               + T_Ice_callback(updateLocation)
               + T_Platform.runLater()
               + T_executeScript(updateBus)
               + T_Leaflet_setLatLng()
               ≈ 2-5 ms (red local) + 10-20 ms (JavaFX/Leaflet rendering)
               ≈ 15-25 ms total (estimado)
```

**Driver E3 (estimado):** la latencia esperada en red local es ~15-25 ms, muy por debajo de los 2 s requeridos. Falta confirmarla con la tabla de abajo.

**Elementos clave que garantizan la latencia:**
1. `archiveDatagramAsync()` (AMI) → el archivado NO bloquea el hilo de notificación
2. `notify()` sin `Thread.sleep()` → iteración inmediata de la lista de suscriptores
3. `Platform.runLater()` → despacho asíncrono al hilo JavaFX sin bloquear Ice
4. Eliminación reactiva de suscriptores caídos (`LocalException`) → el loop de notify no se atasca

| Bus ID | Timestamp event-processor | Timestamp aparición en mapa | Latencia | Cumple < 2 s? |
|--------|---------------------------|------------------------------|----------|--------------|
| _(pendiente)_ | _(pendiente)_ | _(pendiente)_ | _(ms)_ | Esperado: Sí |
