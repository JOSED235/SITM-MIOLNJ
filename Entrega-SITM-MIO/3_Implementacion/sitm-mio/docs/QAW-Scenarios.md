# Drivers de Arquitectura — Escenarios QAW
## Sistema SITM-MIO: Monitoreo en Tiempo Real y Cálculo de Velocidad Promedio
### Universidad Icesi — Ingeniería de Software 4

---

## Contexto del Sistema

El Centro de Control de Operación (CCO) de Metrocali supervisa ~1000 buses que emiten datagramas cada 20-30 s via GPRS. El sistema recibe entre 2.5 y 3 millones de eventos diarios. El piloto cubre un año de datos (~900 M registros anuales estimados) y tiene dos objetivos principales:

1. **Monitoreo en tiempo real:** visualizar posición de todos los buses en un mapa de Cali.
2. **Análisis histórico:** calcular velocidad promedio por ruta (`lineId`) por mes.

---

## Tabla de Priorización de Drivers

| Prioridad | Driver | Atributo de Calidad | Justificación de negocio |
|:---------:|--------|---------------------|--------------------------|
| 1 | **Correctitud del cálculo** | Correctitud | El CCO usa las velocidades para reportar a la ciudadanía; un error compromete la credibilidad |
| 2 | **Latencia de visualización** | Rendimiento | Un operador que ve posiciones con > 2 s de retraso no puede reaccionar a incidentes |
| 3 | **Escalabilidad horizontal** | Escalabilidad | La flota puede crecer de 1000 a 2500 buses; el sistema debe crecer sin reescribirse |
| 4 | **Rendimiento del cálculo** | Rendimiento | El reporte mensual debe completarse en tiempo razonable para no bloquear al CCO |
| 5 | **Disponibilidad parcial** | Disponibilidad | Si un worker falla, el cálculo debe continuar con resultado parcial señalado |
| 6 | **Modificabilidad** | Modificabilidad | Agregar workers o cambiar el número de rutas no debe requerir cambios de código |

---

## Escenario 1 — Correctitud del Cálculo de Velocidad

| Campo | Descripción |
|---|---|
| **Atributo de calidad** | Correctitud |
| **Fuente del estímulo** | Gestor de calidad de Metrocali que valida el reporte piloto |
| **Estímulo** | Solicitud de velocidad promedio mensual sobre una muestra de 1000 datagramas con resultado manual conocido |
| **Artefacto** | Motor de cálculo (`SpeedEngine` en V1/V2, `SpeedWorkerI` en V3) |
| **Entorno** | Dataset de validación con viajes completos y ordenados cronológicamente |
| **Respuesta** | Las tres versiones (V1, V2, V3) producen el mismo valor, consistente con el cálculo manual |
| **Medida de respuesta** | Error < 0.5% respecto al cálculo de referencia; V1, V2 y V3 difieren entre sí en < 0.01% |

**Decisión arquitectónica:** Algoritmo único en `SpeedEngine` (compartido por V1 y V2) y replicado fielmente en `SpeedWorkerI` (V3). El cálculo es **Distancia Total / Tiempo Total** por viaje (`tripId`), con ordenamiento cronológico previo y filtros: descartar Δodómetro ≤ 0 (reset de parada) y velocidades > 120 km/h (dato atípico).

**Trade-off:** Usar el mismo algoritmo en todos los módulos garantiza correctitud pero introduce duplicación entre `SpeedEngine` y `SpeedWorkerI`. Alternativa rechazada: que el worker llame al engine de una librería compartida introduciría acoplamiento en el classpath de los workers distribuidos.

---

## Escenario 2 — Rendimiento del Cálculo Histórico

| Campo | Descripción |
|---|---|
| **Atributo de calidad** | Rendimiento / Performance |
| **Fuente del estímulo** | Operador del CCO que solicita reporte mensual de todas las rutas activas |
| **Estímulo** | Solicitud de cálculo para las 241 rutas activas sobre un año completo de datos (~900 M registros) |
| **Artefacto** | Módulo de cálculo de velocidad (V1 / V2 / V3 según el despliegue) |
| **Entorno** | Operación normal; servidor del CCO disponible |
| **Respuesta** | El sistema calcula y muestra velocidades promedio por ruta por mes |
| **Medida de respuesta** | Dataset MiniPilot: V1 < 60 s, V2 < 20 s; Dataset completo (9×): V3 con 2 workers < 120 s |

**Decisión arquitectónica:** Tres versiones comparadas experimentalmente para identificar el umbral donde la distribución compensa el overhead de comunicación Ice. V1 (baseline), V2 (paralelismo por núcleo), V3 (paralelismo entre máquinas).

**Trade-off frente a Escalabilidad:** Maximizar rendimiento en una sola máquina (V2) es más eficiente para datasets pequeños (< 500K datagramas/partición). Para datasets mayores, el overhead de serialización Ice en V3 se amortiza y la escalabilidad horizontal domina.

---

## Escenario 3 — Latencia de Visualización en Tiempo Real

| Campo | Descripción |
|---|---|
| **Atributo de calidad** | Rendimiento — Latencia |
| **Fuente del estímulo** | Bus que emite un datagrama de posición GPS |
| **Estímulo** | Datagrama recibido en el `event-processor` con nueva coordenada del bus |
| **Artefacto** | Pipeline completo: `event-processor` → `MonitoringSubscriberI` → `JavaFX WebView` → `map.html` |
| **Entorno** | Operación normal; visualizador suscrito y con mapa cargado |
| **Respuesta** | El marcador del bus en el mapa de Cali se mueve a la nueva posición |
| **Medida de respuesta** | Latencia total < 2 s desde que el `event-processor` recibe el datagrama hasta que el marcador se actualiza en pantalla |

**Decisión arquitectónica:** Patrón **Observer / Pub-Sub** con callback Ice. El `event-processor` mantiene una lista de `MonitoringSubscriberPrx` y llama `updateLocation(BusUpdate)` sincrónicamente. El `visualizer-client` implementa el servant `MonitoringSubscriberI` y despacha la actualización al hilo de JavaFX mediante `Platform.runLater()`, que invoca `updateBus()` en el mapa Leaflet.

**Trade-off frente a Disponibilidad:** El callback síncrono garantiza baja latencia pero si el visualizador se desconecta, el `event-processor` recibe una `LocalException`. La decisión: el event-processor elimina al suscriptor fallido de la lista (tolerancia a fallos parcial) para no bloquear a los demás suscriptores ni al flujo de archivado.

---

## Escenario 4 — Escalabilidad Horizontal del Cálculo

| Campo | Descripción |
|---|---|
| **Atributo de calidad** | Escalabilidad |
| **Fuente del estímulo** | Crecimiento de la flota de 1000 a 2500 buses (×2.5 de datagramas) |
| **Estímulo** | El dataset anual crece de ~900 M a ~2.25 mil M de registros |
| **Artefacto** | Subsistema distribuido (`speed-master` + `speed-worker`) |
| **Entorno** | Clúster de nodos workers Ice; operación en producción |
| **Respuesta** | El sistema escala agregando nodos worker sin modificar código ni recompilar |
| **Medida de respuesta** | Speedup ≥ 0.8 × N workers (eficiencia mínima del 80%); latencia de comunicación Ice < 5% del tiempo total de cómputo con dataset completo |

**Decisión arquitectónica:** Patrón **Master-Worker (Scatter-Gather)**. La partición por `lineId` en round-robin garantiza carga balanceada entre workers. Agregar un nuevo worker solo requiere registrar su endpoint en `speed-master.cfg`; no se requiere ningún cambio de código.

**Trade-off frente a Correctitud:** El particionamiento round-robin puede asignar a un worker todas las rutas con mayor volumen de datos, generando desbalance. Solución implementada: la partición es por `lineId` completo (no por datagrama individual), lo cual mantiene la correctitud del cálculo (todos los datagramas de un viaje están en el mismo worker) a costa de no garantizar balanceo perfecto de carga.

---

## Escenario 5 — Disponibilidad Parcial ante Falla de Worker

| Campo | Descripción |
|---|---|
| **Atributo de calidad** | Disponibilidad |
| **Fuente del estímulo** | Falla de red o crash de un nodo worker Ice durante el cálculo |
| **Estímulo** | El `speed-master` recibe `ConnectionRefusedException` al intentar verificar un worker con `checkedCast` |
| **Artefacto** | `speed-master` (SITM.Main) |
| **Entorno** | Operación con 2+ workers; uno de ellos falla antes del inicio del cálculo |
| **Respuesta** | El master descarta el worker fallido, reasigna su partición a los workers disponibles y completa el cálculo |
| **Medida de respuesta** | El cálculo completa con los N-1 workers restantes; resultado completo si todos los datagramas estaban en workers activos; resultado parcial con aviso explícito si se pierden datagramas |

**Decisión arquitectónica:** `checkedCast` antes de distribuir filtra workers no disponibles. Las llamadas a workers se hacen en `Future` separados con `ExecutorService`; si un `Future.get()` lanza excepción, el master registra el error y continúa con los resultados disponibles.

**Trade-off frente a Completitud:** La tolerancia a fallos parcial acepta que el reporte sea incompleto antes de bloquear indefinidamente. Alternativa rechazada: reintentar en otro worker implicaría lógica de redistribución compleja sin garantía de correctitud si el datagrama ya fue parcialmente procesado.

---

## Escenario 6 — Modificabilidad del Despliegue

| Campo | Descripción |
|---|---|
| **Atributo de calidad** | Modificabilidad |
| **Fuente del estímulo** | Administrador del CCO que necesita añadir 2 workers adicionales al clúster |
| **Estímulo** | Solicitud de escalar de 2 a 4 workers sin recompilar el sistema |
| **Artefacto** | Archivo `speed-master.cfg` |
| **Entorno** | Sistema en operación; workers nuevos ya desplegados en nuevas máquinas |
| **Respuesta** | El master reconoce los nuevos workers y distribuye la carga entre los 4 nodos |
| **Medida de respuesta** | Tiempo de cambio < 5 minutos (editar cfg + reiniciar master); cero cambios de código |

**Decisión arquitectónica:** Toda la topología de workers se lee desde `speed-master.cfg` en tiempo de arranque (`Worker.Count`, `Worker.0`, `Worker.1`, ...). El contrato Ice (`SpeedWorker.computeSpeeds`) está versionado en `sitm.ice`; agregar workers no rompe la interfaz.

**Trade-off frente a Rendimiento:** Leer la topología desde archivo introduce un segundo de overhead en el arranque del master. Alternativa considerada: registry Ice (IceGrid) para descubrimiento dinámico de workers, rechazada por complejidad de configuración innecesaria para el piloto.

---

## Relaciones y Trade-offs entre Drivers

```
Correctitud (P1) ←──────────── TENSIÓN ──────────────→ Rendimiento (P2/P4)
  Mismo algoritmo en todos                               Paralelismo puede alterar
  los módulos garantiza                                  el orden de procesamiento;
  resultados idénticos.                                  se resuelve ordenando por
                                                         datagramDate antes de calcular.

Latencia Visualización (P3) ←── TENSIÓN ──────────────→ Disponibilidad (P5)
  Callback síncrono minimiza                             Si el suscriptor falla,
  latencia de entrega.                                   el event-processor lo
                                                         elimina de la lista.

Escalabilidad (P3) ←──────────── TENSIÓN ──────────────→ Correctitud (P1)
  Partición round-robin por                               Garantizada: todos los
  lineId puede crear desbalance.                          datagramas de un viaje
                                                          están en el mismo worker.

Modificabilidad (P6) ←───────────────────────────────── SINERGIA ──→ Escalabilidad (P3)
  La configuración externalizada en .cfg es el mismo mecanismo que permite
  agregar workers sin recompilar (modificabilidad) y escalar horizontalmente.
```
