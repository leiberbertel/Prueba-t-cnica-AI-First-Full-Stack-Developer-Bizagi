# ADR-0009 · Despliegue continuo con GitHub Actions y OIDC

- **Estado:** Aceptado
- **Fecha:** 2026-10-06

## Contexto

El despliegue se hacía a mano con `infra/azure/deploy.sh`: dependía de una sesión de Azure en el equipo del
desarrollador y de que alguien recordara correrlo después de cada cambio. Eso abre dos riesgos: que producción quede
desalineada con `main`, y que se despliegue código que no pasó el CI.

## Decisión

Un job **`deploy`** en `.github/workflows/ci.yml`:

| Regla | Cómo se cumple |
|---|---|
| Solo se despliega lo que pasó las pruebas | `needs: [docker]`, que a su vez depende de `backend` y `frontend` |
| Solo desde `main` | `if: push && refs/heads/main`. Un pull request ejecuta el CI pero nunca despliega |
| Sin contraseñas | **OIDC**: GitHub emite un token por ejecución y Azure lo acepta por una *credencial federada* que solo confía en `repo:<owner>/<repo>:ref:refs/heads/main` |
| Mínimo privilegio | Identidad propia (`polla-github-deploy`), distinta de la que usan las apps. Solo tiene `AcrPush` en el registro y `Contributor` en `polla-api`, `polla-web` y su entorno. **Sin acceso** a PostgreSQL, Key Vault ni el resto del grupo |
| Trazabilidad | La imagen se etiqueta con los 7 primeros caracteres del commit: lo que corre en producción es un commit exacto |
| Sin despliegues cruzados | `concurrency: deploy-production` impide dos despliegues simultáneos |
| Verificación | *Smoke test*: el job falla si la API no responde `204` en `/auth/refresh` tras actualizar |

En GitHub solo se guardan tres **variables** (no secretos): los IDs de cliente, tenant y suscripción.

> **Formato del *subject*.** GitHub firma el token con el formato que incluye los **IDs inmutables** del dueño y del
> repositorio: `repo:<owner>@<owner-id>/<repo>@<repo-id>:ref:refs/heads/main`. La credencial federada debe tener ese
> valor exacto (con el formato antiguo, sin IDs, Azure responde `AADSTS700213`). Es más seguro: si alguien borra y
> recrea un repositorio con el mismo nombre, los IDs ya no coinciden y Azure rechaza el token.

`infra/azure/deploy.sh` sigue existiendo para **crear** la infraestructura desde cero (Key Vault, PostgreSQL, entorno,
identidades). El día a día lo hace el CI.

## Alternativas consideradas

- **Secreto de un *service principal* en GitHub:** funciona, pero es una contraseña que se puede filtrar y que vence.
  Además, registrar aplicaciones en el directorio de la universidad requiere permisos de administrador.
- **Rol `Contributor` sobre todo el grupo de recursos:** más simple, pero un workflow comprometido podría borrar la base
  de datos.
- **Despliegue manual:** es lo que había; depende de las personas.

## Consecuencias

- (+) Cada push a `main` que pasa las pruebas llega a producción en minutos, sin intervención.
- (+) No hay credenciales de larga duración en ningún lado.
- (−) Las imágenes se construyen dos veces en `main` (job `docker` para validar, `deploy` para publicar). Optimizable
  publicando desde el job `docker`; hoy el costo es un par de minutos de CI.
- (−) Si cambia la infraestructura (no solo las imágenes), sigue haciendo falta `deploy.sh`.
