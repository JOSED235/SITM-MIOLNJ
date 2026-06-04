# Drivers de Arquitectura — Escenarios QAW
## Sistema SITM-MIO: Cálculo de Velocidad Promedio por Ruta

---

## Escenario 1 — Rendimiento (Performance)

| Campo | Descripción |
|---|---|
| **Atributo de calidad** | Rendimiento / Performance |
| **Fuente del estímulo** | Operador del CCO que solicita reporte de velocidad promedio |
| **Estímulo** | Solicitud de cálculo para todas las rutas activas de un año |
| **Artefacto** | Sistema de procesamiento de datagramas |
| **Entorno** | Operación normal con dataset piloto (~3 millones de registros) |
| **Respuesta** | El sistema calcula y devuelve la velocidad promedio por ruta por mes |
| **Medida de respuesta** | Tiempo de cómputo < 60 s para dataset MiniPilot; < 300 s para dataset completo |

**Decisión arquitectónica:** Se implementan tres versiones (monolítica, concurrente, distribuida) para identificar el umbral a partir del cual la distribución genera ganancia real frente al overhead de comunicación Ice.

---

## Escenario 2 — Escalabilidad (Scalability)

| Campo | Descripción |
|---|---|
| **Atributo de calidad** | Escalabilidad |
| **Fuente del estímulo** | Crecimiento de la flota de 1000 a 2500 buses |
| **Estímulo** | Volumen de datagramas aumenta en factor ~2.5 (7.5 M eventos/día) |
| **Artefacto** | Módulo de cálculo distribuido (speed-master / speed-worker) |
| **Entorno** | Clúster de nodos workers Ice |
| **Respuesta** | El sistema escala agregando nodos worker sin modificar el master |
| **Medida de respuesta** | Tiempo de cómputo escala linealmente con el número de workers; speedup ≥ 0.8×N |

**Decisión arquitectónica:** Patrón Master-Worker con particionamiento por `lineId`. Agregar un worker solo requiere actualizar `speed-master.cfg` con el nuevo endpoint.

---

## Escenario 3 — Correctitud (Correctness)

| Campo | Descripción |
|---|---|
| **Atributo de calidad** | Correctitud |
| **Fuente del estímulo** | Gestor de calidad que valida el resultado del piloto |
| **Estímulo** | Cálculo de velocidad promedio sobre muestra de 1000 datagramas con resultado conocido |
| **Artefacto** | Motor de cálculo de velocidad (SpeedEngine / SpeedWorkerI) |
| **Entorno** | Dataset de validación manual |
| **Respuesta** | Los tres sistemas (V1, V2, V3) producen el mismo resultado |
| **Medida de respuesta** | Error máximo < 0.5% respecto al cálculo manual de referencia |

**Decisión arquitectónica:** Algoritmo centralizado en `SpeedEngine` (V1/V2) y `SpeedWorkerI` (V3), ambos con la misma lógica: Δodómetro / Δtiempo × 3.6 → km/h, con filtro de resets de parada (odómetro decreciente) y velocidades irreales (> 120 km/h).

---

## Escenario 4 — Disponibilidad (Availability)

| Campo | Descripción |
|---|---|
| **Atributo de calidad** | Disponibilidad |
| **Fuente del estímulo** | Falla de un nodo worker Ice durante el cálculo |
| **Estímulo** | Un worker deja de responder (excepción `LocalException`) |
| **Artefacto** | Speed Master |
| **Entorno** | Operación normal con 2+ workers activos |
| **Respuesta** | El master detecta la falla, registra el error y continúa con los workers disponibles |
| **Medida de respuesta** | El cálculo completa con los workers restantes; resultado parcial claramente señalado |

**Decisión arquitectónica:** El master usa `checkedCast` para verificar disponibilidad antes de distribuir. Las llamadas a workers se hacen en hilos separados (`ExecutorService`); si un `Future` lanza excepción, se omite ese worker y se registra en consola.
