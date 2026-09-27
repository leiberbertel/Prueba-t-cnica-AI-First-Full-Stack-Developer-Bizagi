# ADR-0003 · Recálculo de puntos por eventos de dominio

- **Estado:** Aceptado
- **Fecha:** 2026-09-27

## Contexto

Cuando el admin registra (o corrige) un resultado, deben recalcularse los puntos de todas las predicciones de ese
partido. Opciones:

1. **Calcular al vuelo** en cada lectura del ranking: simple, pero repite trabajo en cada consulta y mezcla la regla
   de puntuación con las consultas.
2. **Llamada directa** `matches → scoring` en la misma transacción: acopla módulos.
3. **Evento de dominio** `MatchResultRegistered` escuchado por `scoring`.

## Decisión

Opción 3, con el **Event Publication Registry** de Spring Modulith (tabla `event_publication`):

- `matches` publica el evento dentro de la transacción que guarda el resultado.
- `scoring` lo procesa **después del commit** (`@ApplicationModuleListener`) en su propia transacción.
- Si el listener falla, la publicación queda **incompleta** en la tabla y se reintenta al reiniciar
  (garantía *at-least-once*, patrón outbox).
- Por eso el recálculo es **idempotente**: `UPDATE predictions SET points = f(pred, real) WHERE match_id = ?`
  sobrescribe los puntos, así que procesar el evento dos veces no duplica nada.

## Consecuencias

- (+) `matches` no conoce a `scoring`. Agregar nuevos consumidores (notificaciones, auditoría, snapshot del ranking)
  es agregar un listener.
- (+) Los puntos se guardan (`predictions.points`), así que el ranking es una agregación barata.
- (−) Consistencia eventual de milisegundos entre registrar el resultado y ver los puntos. En las pruebas de
  integración se espera el evento con `Scenario` de Spring Modulith.
