# ADR-0007 · Eliminación de cuentas asíncrona, por lotes y con timeouts

- **Estado:** Aceptado
- **Fecha:** 2026-09-27

## Contexto

HU-01.6 permite que un participante elimine su cuenta y todos sus datos (derecho de supresión, Ley 1581 de 2012).
La primera implementación borraba la fila del usuario y dejaba que PostgreSQL eliminara predicciones y sesiones con
`ON DELETE CASCADE`, en **una sola transacción**. Al revisarla surgieron dos problemas:

1. **Volumen.** Si un usuario acumula muchos registros (hoy los refresh tokens crecen sin límite: uno por cada
   renovación de sesión), el borrado en cascada es una transacción larga. Bloquea cada fila borrada, deja la petición
   HTTP colgada, genera un pico de WAL y trabajo de *vacuum*, y en un servidor pequeño (B1ms, 1 vCPU con créditos)
   degrada a **todos** los usuarios. Si falla a la mitad, se revierte todo.
2. **Límites entre módulos.** El `CASCADE` hace que borrar en `auth` elimine datos de `predictions`, **saltándose** los
   límites que Spring Modulith verifica en el código (ADR-0002).

## Decisión

Eliminación en **dos fases**. Cada módulo borra **sus propios** datos, siempre en transacciones cortas y con timeouts.

```
DELETE /me ──► [auth] transacción ≤ 5 s:
                 deleted_at = now, email/nombre anonimizados, sesiones revocadas
                 + UserAccountDeleted en el outbox (Event Publication Registry)   ──► 202 Accepted
                        │
          ┌─────────────┴──────────────┐
          ▼                            ▼
 [predictions] borra sus         [auth] borra sus refresh tokens
 predicciones por lotes          por lotes
          │                            │
          └────────► [auth] tarea programada: borra la fila del usuario.
                     La FK RESTRICT lo impide mientras queden datos → se reintenta después.
```

| Decisión | Detalle |
|---|---|
| Supresión inmediata | La fase síncrona ya elimina los datos personales (anonimiza email y nombre, invalida la contraseña). Lo que queda en segundo plano no identifica a la persona. |
| Lotes | `DELETE … WHERE id IN (SELECT id … LIMIT n FOR UPDATE SKIP LOCKED)`. Tamaño configurable (`app.account-deletion.batch-size`, 5.000 por defecto). `SKIP LOCKED` evita esperar filas que otra transacción tenga bloqueadas, y permite varias réplicas purgando en paralelo sin chocar. |
| Timeouts por lote | `SET LOCAL lock_timeout = '2s'` y `statement_timeout = '10s'` dentro de cada transacción de purga. Un lote bloqueado **falla rápido** en vez de acumular esperas. |
| Timeouts globales | Toda conexión de la app: `lock_timeout = 5s`, `statement_timeout = 15s`. Pool: esperar conexión máximo 5 s. `DELETE /me`: transacción de 5 s. |
| Reintentos | Si un lote falla, la publicación del evento queda **incompleta** en el outbox. Una tarea programada reenvía las publicaciones incompletas con más de 5 minutos. El borrado es idempotente, así que repetirlo es seguro. |
| Integridad | `predictions.user_id` y `refresh_tokens.user_id` pasan de `ON DELETE CASCADE` a `RESTRICT`: nadie puede borrar un usuario con datos pendientes, ni por error. |
| Sesiones activas | Un access token emitido antes de eliminar la cuenta (hasta 15 min) no permite escribir: `predictions` consulta `AccountDirectory.isActive` antes de guardar. Las lecturas ya no muestran al usuario (ranking e historial lo excluyen). |
| Limpieza de tokens | Otra tarea programada purga por lotes los refresh tokens vencidos, así la tabla no crece sin límite. |

## Alternativas consideradas

- **`ON DELETE CASCADE` en una transacción** (versión anterior): simple, pero sin límite de duración ni de bloqueos, y
  cruza los límites entre módulos.
- **Solo anonimizar (sin borrar):** rápido, pero conserva datos que el usuario pidió eliminar.
- **Cola externa (Service Bus / Kafka):** innecesaria hoy. El outbox de Spring Modulith ya da reintentos y
  persistencia, y el evento se puede externalizar después sin cambiar los módulos (ADR-0002).

## Consecuencias

- (+) La petición del usuario responde en milisegundos, sea cual sea el volumen de datos.
- (+) Ninguna operación de purga puede bloquear la base de datos más allá de sus timeouts.
- (+) Cada módulo es dueño de sus datos; la base de datos solo garantiza el orden (RESTRICT).
- (−) Consistencia eventual: durante unos segundos las predicciones del usuario siguen existiendo (ya anonimizadas y
  ocultas del ranking). El conteo de predicciones del panel admin puede incluirlas brevemente.
- (−) Más piezas: dos listeners, dos tareas programadas y una migración. Se cubren con pruebas de integración,
  incluida una que bloquea filas a propósito para comprobar que el timeout falla rápido.
