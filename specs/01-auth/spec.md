# 01 · Autenticación y usuarios

**Módulo backend:** `auth` · **Feature frontend:** `features/auth`
**Contrato:** tag `Auth` en [`openapi.yaml`](../api/openapi.yaml)

## Historias de usuario

- **HU-01.1** Como visitante quiero **registrarme** con email, nombre visible y contraseña para participar en la polla.
- **HU-01.2** Como usuario registrado quiero **iniciar sesión** para hacer mis predicciones.
- **HU-01.3** Como usuario quiero que mi **sesión se mantenga** al recargar la página sin volver a escribir la contraseña.
- **HU-01.4** Como usuario quiero **cerrar sesión** de forma segura.
- **HU-01.5** Como admin quiero que mi cuenta exista desde el despliegue, sin depender del registro público.
- **HU-01.6** Como participante quiero **eliminar mi cuenta y todos mis datos** (derecho de supresión, Ley 1581 de 2012).

## Roles

| Rol | Puede |
|---|---|
| `USER` | Ver partidos, crear/editar sus predicciones, ver ranking e historiales. |
| `ADMIN` | Ver partidos, ranking e historiales + registrar/corregir resultados. **No participa** en la polla: no predice ni aparece en el ranking (quien carga los resultados no debe competir, por conflicto de interés). |

## Criterios de aceptación

| ID | Dado / Cuando / Entonces |
|---|---|
| CA-01.1 | Dado un email no registrado y datos válidos, cuando me registro, entonces se crea un usuario `USER`, recibo un access token y quedo autenticado. |
| CA-01.2 | Dado un email ya registrado (sin importar mayúsculas), cuando intento registrarme, entonces recibo `409 Conflict`. |
| CA-01.3 | Contraseña: 8–72 caracteres, al menos una letra y un número. Si no cumple → `400` con detalle por campo. |
| CA-01.4 | Nombre visible: 2–40 caracteres. Email válido, máximo 254. |
| CA-01.5 | Dadas credenciales inválidas, cuando hago login, entonces recibo `401` con mensaje **genérico** (no revela si el email existe). |
| CA-01.6 | Login y registro devuelven un **access token** (JWT, 15 min) en el body y un **refresh token** en cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`. |
| CA-01.7 | `POST /auth/refresh` con cookie válida devuelve un nuevo access token y **rota** el refresh token (el anterior queda revocado). **Sin cookie** (nunca se inició sesión) responde `204 No Content`: no tener sesión es un estado normal, no un error. Con cookie inválida, vencida o revocada → `401`. |
| CA-01.8 | Reutilizar un refresh token revocado → `401` y se revocan **todos** los refresh tokens del usuario (detección de robo). |
| CA-01.9 | `POST /auth/logout` revoca el refresh token actual y borra la cookie → `204`. |
| CA-01.10 | Más de 10 intentos de login por IP por minuto → `429 Too Many Requests`. La IP es la **real** del cliente: un `X-Forwarded-For` escrito por el cliente no permite evadir el límite (la API lee la cadena de derecha a izquierda saltando solo proxies de confianza; nginx descarta el encabezado cuando es el borde). |
| CA-01.11 | Al arrancar, si no existe un usuario con `ADMIN_EMAIL`, se crea con rol `ADMIN` y `ADMIN_PASSWORD` (variables de entorno). |
| CA-01.12 | Endpoints protegidos sin token o con token expirado → `401`. Con rol insuficiente → `403`. |
| CA-01.13 | `DELETE /me` con mi contraseña correcta responde `202 Accepted` y borra la cookie de refresh. **De inmediato:** la cuenta queda marcada como eliminada, el email y el nombre se anonimizan, todas mis sesiones se revocan, no puedo iniciar sesión ni predecir, y el ranking deja de mostrarme. **En segundo plano:** mis predicciones, mis refresh tokens y finalmente la fila de usuario se borran definitivamente, por lotes. |
| CA-01.14 | Con contraseña incorrecta → `403` (`invalid-password`) y la cuenta queda intacta. Se usa 403 y no 401 porque la sesión es válida: falla la re-autenticación. |
| CA-01.15 | La cuenta `ADMIN` no se puede eliminar → `409` (`admin-cannot-be-deleted`) (RN-10). |
| CA-01.16 | Tras eliminar la cuenta, el mismo email puede registrarse **de inmediato** como una cuenta nueva y vacía (el email anterior ya fue anonimizado). |
| CA-01.17 | Un access token emitido antes de eliminar la cuenta (vigente hasta 15 min) ya no permite crear ni modificar predicciones → `401`. |
| CA-01.18 | La purga nunca bloquea la base de datos: cada lote tiene `lock_timeout` y `statement_timeout`. Si un lote falla por timeout, se reintenta después sin perder la solicitud (ADR-0007). |
| CA-01.19 | Los refresh tokens vencidos se purgan periódicamente, por lotes (la tabla no crece sin límite). |

## Diseño

### Tokens

- **Access token:** JWT firmado HS256 con Spring Security (`NimbusJwtEncoder`/`Decoder`, sin librerías de terceros).
  Claims: `sub` (userId), `email`, `name`, `roles`, `iat`, `exp`. El front lo guarda **solo en memoria** (signal).
- **Refresh token:** valor aleatorio de 256 bits. En BD se guarda **solo su hash SHA-256**, con `expires_at` (7 días)
  y `revoked_at`. Así, una fuga de la BD no permite suplantar sesiones.

> Justificación completa en [ADR-0004](../adr/0004-estrategia-tokens.md).

### Flujo de sesión en el frontend

1. Al iniciar la app se llama `POST /auth/refresh`. Si responde 200, la sesión se restaura (HU-01.3); si responde 204,
   no hay sesión y la app sigue como visitante sin registrar ningún error.
2. Un interceptor funcional agrega `Authorization: Bearer <token>` a cada request de la API.
3. Ante un `401`, el interceptor intenta **un** refresh (compartido entre requests concurrentes) y reintenta; si falla, va a `/login`.

### Modelo de datos

```
users(id, email UNIQUE(lower), display_name, password_hash, role, created_at)
refresh_tokens(id, user_id FK, token_hash UNIQUE, expires_at, revoked_at, created_at)
```

### Eliminación de cuenta (HU-01.6)

Diseño completo en [ADR-0007](../adr/0007-eliminacion-asincrona-de-cuentas.md). Resumen:

- **Re-autenticación:** se exige la contraseña aunque haya sesión. Un access token robado (15 min) no basta.
- **Borrado en dos fases:**
  1. *Síncrona y corta* (máx. 5 s): `deleted_at`, anonimización de datos personales, revocación de sesiones, evento
     `UserAccountDeleted` en el outbox. Responde `202`. Cumple el derecho de supresión **al instante**: ya no quedan
     datos que identifiquen a la persona.
  2. *Asíncrona y por lotes:* cada módulo borra **sus propios** datos (`predictions`, `auth`) en transacciones cortas
     con timeouts. Una tarea programada borra la fila del usuario cuando ya no tiene datos: la FK `RESTRICT` lo impide
     mientras queden predicciones.
- **Frontend:** opción "Eliminar mi cuenta" en el menú de usuario (oculta para el admin). Diálogo que explica qué se
  borra, pide la contraseña y escribir `ELIMINAR` **exactamente, en mayúsculas**. Al terminar se cierra la sesión y se muestra un aviso en el login.

## Casos borde

- Email con espacios o mayúsculas → se normaliza (`trim` + minúsculas) antes de guardar y comparar.
- Contraseña de más de 72 bytes → rechazada (límite de BCrypt; evita truncamiento silencioso).
- Dos refresh simultáneos desde el front → el interceptor comparte una sola llamada en vuelo.
