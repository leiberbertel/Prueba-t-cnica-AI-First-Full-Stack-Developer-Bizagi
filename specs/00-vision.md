# 00 · Visión y alcance

> Metodología: **Spec-Driven Development (SDD)**. Cada módulo se especifica aquí *antes* de implementarse.
> El contrato HTTP vive en [`api/openapi.yaml`](api/openapi.yaml) y es la **fuente de verdad** entre front y back.
> Si el código y la spec discrepan, se corrige primero la spec y luego el código.

## Problema

Un grupo privado de personas quiere jugar una "polla mundialista": predecir marcadores de partidos,
sumar puntos según aciertos y ver una tabla de posiciones. Un administrador carga los resultados reales.

## Alcance (in scope)

| Módulo | Spec | Resumen |
|---|---|---|
| 1. Autenticación y usuarios | [01-auth](01-auth/spec.md) | Registro, login, JWT, roles `USER` y `ADMIN` |
| 2. Predicciones y lógica de negocio | [02-predictions](02-predictions/spec.md) | 12 partidos precargados (2 grupos), predicciones, puntuación |
| 3. Panel de administración | [03-admin-results](03-admin-results/spec.md) | Admin registra/corrige el resultado final |
| 4. Leaderboard y dashboard | [04-leaderboard](04-leaderboard/spec.md) | Ranking global, historial de predicciones por usuario |

## Fuera de alcance (explícito)

- Múltiples pollas/grupos, invitaciones, pagos o premios.
- Fases eliminatorias, prórrogas y penales.
- Recuperación de contraseña por correo y verificación de email.
- Integración con proveedores de resultados en tiempo real.

> Estas exclusiones son decisiones, no olvidos: el diseño modular permite agregarlas sin reescribir módulos existentes
> (ver [ADR-0002](adr/0002-monolito-modular.md)).

## Glosario

| Término | Definición |
|---|---|
| Partido (`Match`) | Encuentro de fase de grupos entre dos selecciones, con fecha/hora de inicio (`kickoffAt`). |
| Predicción (`Prediction`) | Marcador que un usuario pronostica para un partido. Máximo una por usuario y partido. |
| Resultado | Marcador real registrado por el admin. Un partido con resultado está `FINISHED`. |
| Cierre de predicciones | Momento a partir del cual no se aceptan predicciones: `kickoffAt` o partido `FINISHED`. |
| Acierto exacto | La predicción coincide en goles local y visitante. **3 puntos.** |
| Acierto de ganador/empate | La predicción acierta quién gana (o que es empate) sin marcador exacto. **1 punto.** |

## Reglas de negocio transversales

| ID | Regla |
|---|---|
| RN-01 | Un usuario tiene como máximo **una predicción por partido**; puede modificarla hasta el cierre. |
| RN-02 | No se aceptan predicciones después del `kickoffAt` ni de partidos `FINISHED` (validado en backend). |
| RN-03 | Goles válidos: enteros entre 0 y 20 (predicciones y resultados). |
| RN-04 | Puntuación: 3 (exacto) · 1 (ganador/empate) · 0 (fallo). Los valores son configurables. |
| RN-05 | Registrar o **corregir** un resultado recalcula los puntos de todas las predicciones de ese partido de forma **idempotente**. |
| RN-06 | Las predicciones de otros usuarios solo son visibles cuando el partido ya cerró (evita copiar). |
| RN-07 | Ranking ordenado por: puntos ↓, aciertos exactos ↓, aciertos de ganador ↓, nombre ↑. Empates comparten posición. |
| RN-08 | Nadie puede registrarse como `ADMIN`. El admin se crea por configuración (variables de entorno). |
| RN-09 | El `ADMIN` no participa: no puede predecir y no aparece en el ranking (conflicto de interés). |

## Requerimientos no funcionales

| ID | Requerimiento |
|---|---|
| RNF-01 | Separación clara front-end (Angular 21) / back-end (Spring Boot 4) comunicados solo por el contrato OpenAPI. |
| RNF-02 | Base de datos relacional (PostgreSQL) con esquema versionado por migraciones (Flyway). |
| RNF-03 | Contraseñas con BCrypt; access token JWT de vida corta; refresh token en cookie `HttpOnly`. |
| RNF-04 | Backend *stateless*: escala horizontalmente sin sesión en memoria. |
| RNF-05 | Errores con formato uniforme RFC 9457 (`application/problem+json`). |
| RNF-06 | Pruebas automatizadas: unitarias (dominio), integración (Postgres real con Testcontainers) y e2e. |
| RNF-07 | Levantar el proyecto localmente con un solo comando (`docker compose up`). |
| RNF-08 | UI responsive (móvil primero) y accesible (WCAG AA en contraste y navegación por teclado). |

## Datos iniciales (seed)

12 partidos de fase de grupos, 2 grupos de 4 selecciones (todos contra todos, 3 fechas).
El torneo es una **edición demo**: equipos reales, calendario ficticio, sin marcas oficiales.

- **Grupo A:** Colombia, Argentina, España, Japón
- **Grupo B:** Brasil, Francia, México, Marruecos

## Decisiones de arquitectura

Registradas como ADRs en [`adr/`](adr/).
