# 01 · Autenticación y usuarios

**Módulo backend:** `auth` (+ `users`) · **Feature frontend:** `features/auth`
**Contrato:** tag `Auth` en [`openapi.yaml`](../api/openapi.yaml)

## Historias de usuario

- **HU-01.1** Como visitante quiero **registrarme** con email, nombre visible y contraseña para participar en la polla.
- **HU-01.2** Como usuario registrado quiero **iniciar sesión** para hacer mis predicciones.
- **HU-01.3** Como usuario quiero que mi **sesión se mantenga** al recargar la página sin volver a escribir la contraseña.
- **HU-01.4** Como usuario quiero **cerrar sesión** de forma segura.
- **HU-01.5** Como admin quiero que mi cuenta exista desde el despliegue, sin depender del registro público.

## Roles

| Rol | Puede |
|---|---|
| `USER` | Ver partidos, crear/editar sus predicciones, ver ranking e historiales. |
| `ADMIN` | Todo lo de `USER` + registrar/corregir resultados. |

## Criterios de aceptación

| ID | Dado / Cuando / Entonces |
|---|---|
| CA-01.1 | Dado un email no registrado y datos válidos, cuando me registro, entonces se crea un usuario `USER`, recibo un access token y quedo autenticado. |
| CA-01.2 | Dado un email ya registrado (sin importar mayúsculas), cuando intento registrarme, entonces recibo `409 Conflict`. |
| CA-01.3 | Contraseña: 8–72 caracteres, al menos una letra y un número. Si no cumple → `400` con detalle por campo. |
| CA-01.4 | Nombre visible: 2–40 caracteres. Email válido, máximo 254. |
| CA-01.5 | Dadas credenciales inválidas, cuando hago login, entonces recibo `401` con mensaje **genérico** (no revela si el email existe). |
| CA-01.6 | Login y registro devuelven un **access token** (JWT, 15 min) en el body y un **refresh token** en cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`. |
| CA-01.7 | `POST /auth/refresh` con cookie válida devuelve un nuevo access token y **rota** el refresh token (el anterior queda revocado). |
| CA-01.8 | Reutilizar un refresh token revocado → `401` y se revocan **todos** los refresh tokens del usuario (detección de robo). |
| CA-01.9 | `POST /auth/logout` revoca el refresh token actual y borra la cookie → `204`. |
| CA-01.10 | Más de 10 intentos de login por IP por minuto → `429 Too Many Requests`. |
| CA-01.11 | Al arrancar, si no existe un usuario con `ADMIN_EMAIL`, se crea con rol `ADMIN` y `ADMIN_PASSWORD` (variables de entorno). |
| CA-01.12 | Endpoints protegidos sin token o con token expirado → `401`. Con rol insuficiente → `403`. |

## Diseño

### Tokens

- **Access token:** JWT firmado HS256 con Spring Security (`NimbusJwtEncoder`/`Decoder`, sin librerías de terceros).
  Claims: `sub` (userId), `email`, `name`, `roles`, `iat`, `exp`. El front lo guarda **solo en memoria** (signal).
- **Refresh token:** valor aleatorio de 256 bits. En BD se guarda **solo su hash SHA-256**, con `expires_at` (7 días)
  y `revoked_at`. Así, una fuga de la BD no permite suplantar sesiones.

> Justificación completa en [ADR-0004](../adr/0004-estrategia-tokens.md).

### Flujo de sesión en el frontend

1. Al iniciar la app se llama `POST /auth/refresh`. Si responde 200, la sesión se restaura (HU-01.3).
2. Un interceptor funcional agrega `Authorization: Bearer <token>` a cada request de la API.
3. Ante un `401`, el interceptor intenta **un** refresh (compartido entre requests concurrentes) y reintenta; si falla, va a `/login`.

### Modelo de datos

```
users(id, email UNIQUE(lower), display_name, password_hash, role, created_at)
refresh_tokens(id, user_id FK, token_hash UNIQUE, expires_at, revoked_at, created_at)
```

## Casos borde

- Email con espacios o mayúsculas → se normaliza (`trim` + minúsculas) antes de guardar y comparar.
- Contraseña de más de 72 bytes → rechazada (límite de BCrypt; evita truncamiento silencioso).
- Dos refresh simultáneos desde el front → el interceptor comparte una sola llamada en vuelo.
