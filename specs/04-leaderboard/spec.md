# 04 · Leaderboard y dashboard

**Módulo backend:** `leaderboard` · **Feature frontend:** `features/leaderboard`
**Contrato:** tag `Leaderboard` en [`openapi.yaml`](../api/openapi.yaml)

## Historias de usuario

- **HU-04.1** Como usuario quiero ver una **tabla de posiciones** global ordenada por puntos.
- **HU-04.2** Como usuario quiero **hacer clic en un participante** y ver su historial de predicciones.
- **HU-04.3** Como usuario quiero ver **mi posición destacada** en la tabla.
- **HU-04.4** Como usuario quiero un pequeño **dashboard** con mis puntos, posición, exactos y partidos pendientes por predecir.

## Criterios de aceptación

| ID | Dado / Cuando / Entonces |
|---|---|
| CA-04.1 | `GET /leaderboard` devuelve a **todos** los participantes (rol `USER`, incluso con 0 puntos; RN-09) con `position`, `points`, `exactHits`, `outcomeHits`, `scoredPredictions`. |
| CA-04.2 | Orden y desempate según RN-07. Usuarios empatados en todos los criterios comparten posición (ranking denso: 1, 2, 2, 3). |
| CA-04.3 | `GET /users/{id}/predictions` devuelve el historial del usuario con partido, predicción, resultado real y puntos. |
| CA-04.4 | Si consulto **mi** historial veo todas mis predicciones. Si consulto el de **otro**, solo veo las de partidos cerrados (RN-06). |
| CA-04.5 | Usuario inexistente o con la cuenta eliminada → `404`. Las cuentas eliminadas desaparecen del ranking **de inmediato**, aunque sus datos aún se estén purgando. |
| CA-04.6 | El ranking refleja un resultado nuevo o corregido inmediatamente después del recálculo. |
| CA-04.7 | `GET /me/summary` devuelve mis puntos, posición, exactos y número de partidos abiertos sin predicción. |

## Diseño

- El ranking se calcula con **una sola consulta SQL agregada** (`LEFT JOIN` usuarios→predicciones, `SUM`, `COUNT FILTER`,
  `DENSE_RANK() OVER (...)`). Con un grupo privado (decenas o cientos de usuarios) responde en milisegundos,
  apoyado en el índice `predictions(user_id)`.
- **Ruta de escalabilidad** (si fueran millones de usuarios): tabla o vista materializada `leaderboard_snapshot`
  actualizada por el mismo evento `MatchResultRegistered`, más caché con invalidación por evento. El contrato HTTP no cambia.
  Ver [ADR-0002](../adr/0002-monolito-modular.md).
