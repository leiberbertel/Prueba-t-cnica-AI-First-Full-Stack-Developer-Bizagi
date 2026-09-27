# 03 · Panel de administración (resultados)

**Módulo backend:** `matches` (casos de uso de admin) · **Feature frontend:** `features/admin`
**Contrato:** tag `Admin` en [`openapi.yaml`](../api/openapi.yaml)

## Historias de usuario

- **HU-03.1** Como admin quiero una **vista protegida** con todos los partidos y un formulario para registrar el resultado final.
- **HU-03.2** Como admin quiero **corregir** un resultado mal digitado y que los puntos se recalculen solos.
- **HU-03.3** Como admin quiero ver cuántas predicciones tiene cada partido antes de registrar el resultado.

## Criterios de aceptación

| ID | Dado / Cuando / Entonces |
|---|---|
| CA-03.1 | La ruta `/admin` del front solo es accesible con rol `ADMIN` (guard). Un `USER` es redirigido y no ve el enlace en el menú. |
| CA-03.2 | `PUT /admin/matches/{id}/result` con rol `ADMIN` registra el resultado → `200`, partido pasa a `FINISHED`. |
| CA-03.3 | El mismo endpoint con rol `USER` → `403` (la seguridad se valida en el **backend**, no solo en la UI). |
| CA-03.4 | Registrar un resultado dispara el recálculo de puntos (RN-05). Registrarlo de nuevo con otro marcador lo **corrige**. |
| CA-03.5 | Goles fuera de 0–20 → `400`. Partido inexistente → `404`. |
| CA-03.6 | La UI pide **confirmación** antes de guardar, mostrando el marcador y el número de predicciones afectadas. |
| CA-03.7 | Dos admins editando a la vez → control de concurrencia optimista (`version`); el segundo recibe `409` y recarga. |

## Decisiones

- **El admin es la fuente de verdad:** el sistema no bloquea registrar un resultado antes del `kickoffAt`
  (útil para la demo y para partidos adelantados). Sí lo marca visualmente en la UI.
- **Auditoría mínima:** se guarda `result_registered_at`. Una bitácora completa (quién cambió qué) queda fuera de alcance,
  pero el evento de dominio es el punto natural para agregarla.
