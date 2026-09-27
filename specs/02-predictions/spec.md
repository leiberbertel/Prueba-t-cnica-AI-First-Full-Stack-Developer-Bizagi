# 02 · Partidos, predicciones y puntuación

**Módulos backend:** `matches`, `predictions`, `scoring` · **Feature frontend:** `features/matches`
**Contrato:** tags `Matches` y `Predictions` en [`openapi.yaml`](../api/openapi.yaml)

## Historias de usuario

- **HU-02.1** Como usuario quiero ver los **12 partidos** agrupados por grupo y fecha, con banderas y hora local.
- **HU-02.2** Como usuario quiero **ingresar o modificar** mi predicción (goles local vs. visitante) mientras el partido esté abierto.
- **HU-02.3** Como usuario quiero ver claramente **cuáles partidos ya cerraron** y cuánto falta para que cierre cada uno.
- **HU-02.4** Como usuario, cuando se registra el resultado real, quiero ver **cuántos puntos** obtuvo mi predicción y por qué.

## Criterios de aceptación

| ID | Dado / Cuando / Entonces |
|---|---|
| CA-02.1 | El sistema arranca con exactamente 12 partidos (2 grupos × 6), cargados por migración/seed idempotente. |
| CA-02.2 | `GET /matches` devuelve los partidos ordenados por `kickoffAt`, cada uno con `predictionOpen` y **mi** predicción (si existe). |
| CA-02.3 | `PUT /matches/{id}/prediction` crea la predicción si no existe o la actualiza si existe (upsert idempotente) → `200`. |
| CA-02.4 | Si el partido cerró (RN-02) → `409 Conflict` con `type` `.../prediction-closed`. |
| CA-02.5 | Goles fuera de 0–20 o no enteros → `400` con detalle por campo. |
| CA-02.6 | Partido inexistente → `404`. |
| CA-02.7 | Cuando el partido tiene resultado, mi predicción muestra `points` (0, 1 o 3) y el resultado real. |
| CA-02.8 | Dos requests simultáneos del mismo usuario para el mismo partido no crean duplicados (restricción `UNIQUE(user_id, match_id)`). |

## Regla de puntuación (RN-04)

```
exacto        = pred.home == real.home && pred.away == real.away          → 3 puntos
mismo desenlace = signo(pred.home - pred.away) == signo(real.home - real.away) → 1 punto
en otro caso                                                               → 0 puntos
```

Tabla de verdad (casos obligatorios en tests unitarios):

| Predicción | Real | Puntos | Motivo |
|---|---|---|---|
| 2-1 | 2-1 | 3 | Exacto |
| 0-0 | 0-0 | 3 | Exacto (empate) |
| 3-1 | 2-0 | 1 | Gana local |
| 1-1 | 2-2 | 1 | Empate |
| 0-2 | 1-3 | 1 | Gana visitante |
| 2-1 | 1-2 | 0 | Desenlace invertido |
| 1-1 | 1-0 | 0 | Empate vs. victoria |
| 0-1 | 0-0 | 0 | Victoria vs. empate |

**Diseño:** la regla vive en una interfaz `ScoringPolicy` (patrón *Strategy*) con una implementación
`StandardScoringPolicy` cuyos valores (`app.scoring.exact-points`, `app.scoring.outcome-points`) son configurables.
Cambiar o extender la regla (p. ej., bonus por goles de un equipo) no toca controladores ni persistencia.

## Recálculo de puntos (RN-05)

1. El módulo `matches` publica el evento de dominio `MatchResultRegistered(matchId, homeGoals, awayGoals)`.
2. El módulo `scoring` lo escucha y **recalcula los puntos de todas las predicciones de ese partido**.
3. El recálculo **sobrescribe** los puntos (no los suma): ejecutarlo 1 o N veces produce el mismo resultado (idempotente).
4. Los eventos se persisten en el *Event Publication Registry* de Spring Modulith: si el listener falla, el evento
   queda pendiente y se reintenta (patrón *outbox*). Ver [ADR-0003](../adr/0003-eventos-de-dominio.md).

## Modelo de datos

```
teams(id, code UNIQUE, name, flag_code, group_code)
matches(id, group_code, matchday, home_team_id FK, away_team_id FK, kickoff_at, venue,
        status, home_goals NULL, away_goals NULL, result_registered_at NULL, version)
predictions(id, user_id FK, match_id FK, home_goals, away_goals, points NULL,
            created_at, updated_at, UNIQUE(user_id, match_id))
```

## Casos borde

- La hora de cierre se valida con el reloj del **servidor** (`Clock` inyectado → testeable), nunca con el del cliente.
- El front muestra la hora en la zona del navegador; el backend trabaja siempre en UTC (`timestamptz`).
- Si el admin corrige un resultado, los puntos cambian y el ranking se actualiza.
