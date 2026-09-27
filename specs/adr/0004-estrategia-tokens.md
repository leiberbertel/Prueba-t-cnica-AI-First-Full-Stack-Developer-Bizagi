# ADR-0004 · Estrategia de tokens (JWT + refresh en cookie HttpOnly)

- **Estado:** Aceptado
- **Fecha:** 2026-09-27

## Contexto

La prueba sugiere "un sistema simple con JWT". Lo más común es guardar el JWT en `localStorage`, pero cualquier XSS
puede leerlo y exfiltrarlo. Tampoco se quiere una sesión con estado en el servidor que impida escalar.

## Decisión

| Token | Vida | Dónde vive | Protege contra |
|---|---|---|---|
| **Access token** (JWT HS256) | 15 min | Memoria de la SPA (signal) | XSS persistente: no queda en storage |
| **Refresh token** (opaco, 256 bits) | 7 días | Cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth` | XSS (JS no puede leerla) y CSRF (`SameSite=Strict` + solo sirve en `/auth/refresh`) |

- En BD solo se guarda el **hash SHA-256** del refresh token.
- **Rotación en cada uso** y **detección de reutilización**: si llega un refresh token ya revocado, se revocan todas
  las sesiones del usuario.
- Front y API se sirven bajo el **mismo origen** (proxy `/api` en desarrollo y en producción), así que la cookie es
  *first-party* y no se ve afectada por el bloqueo de cookies de terceros en los navegadores.

## Alternativas

- **JWT en localStorage:** más simple, pero vulnerable a exfiltración por XSS.
- **Sesión con estado (JSESSIONID):** segura, pero no es *stateless* y requiere afinidad o un store compartido.

## Consecuencias

- (+) Mejor postura de seguridad con un costo de implementación moderado.
- (+) Cerrar sesión es real: se revoca el refresh token.
- (−) El access token sigue siendo válido hasta 15 min tras el logout (compromiso aceptado por usar JWT *stateless*).
