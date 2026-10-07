# CLAUDE.md — Guía para agentes de IA en este repositorio

Proyecto **Polla Mundialista**: backend Spring Boot 4 (Java 21) + frontend Angular 21, PostgreSQL.
Se desarrolla con **Spec-Driven Development**: las specs en `specs/` mandan.

## Flujo obligatorio (SDD)

1. **Leer la spec** del módulo afectado en `specs/0X-*/spec.md` y el contrato `specs/api/openapi.yaml`.
2. Si el cambio altera el comportamiento o la API → **actualizar primero la spec/contrato**, luego el código.
3. Implementar con pruebas: toda regla de negocio (RN-xx) o criterio (CA-xx) nuevo lleva su test.
4. Verificar (ver "Comandos") antes de dar algo por terminado. No declarar éxito sin evidencia.
5. Commits pequeños con Conventional Commits (`feat(scoring): ...`, `test(auth): ...`, `docs(specs): ...`).

## Estructura

```
specs/            Specs por módulo, OpenAPI, ADRs, esquema de BD (fuente de verdad)
backend/          Spring Boot 4 · paquete raíz dev.leiber.polla · módulos Spring Modulith
frontend/         Angular 21 · standalone · zoneless · signals · Angular Material 3
docs/             Arquitectura (C4), AI_LOG, material de presentación
```

## Convenciones backend

- Paquete por **módulo** (`auth`, `matches`, `predictions`, `scoring`, `leaderboard`, `demo`, `shared`).
  El paquete raíz del módulo es su **API pública** (interfaces, eventos, vistas). Dentro, capas fijas:
  - `domain/` → entidades y reglas de negocio (sin lógica de framework cuando sea posible).
  - `application/` → casos de uso (servicios, listeners de eventos, consultas de lectura).
  - `infrastructure/` → repositorios, `@Configuration`, `@ConfigurationProperties`, seeders.
  - `web/` → controllers y DTOs.
  Los subpaquetes son internos para Spring Modulith: otros módulos solo usan el paquete raíz. `ModularityTests` debe pasar.
- Módulos se comunican por **eventos de dominio** (p. ej. `MatchResultRegistered`), no por llamadas a internos.
- Acceso a datos (ADR-0008): JPA para escribir entidades; SQL nativo (`JdbcClient`) solo para lecturas agregadas y
  operaciones masivas. **El SQL vive únicamente en `infrastructure`**, siempre con parámetros con nombre (`:id`).
- DTOs como `record`. Validación con Jakarta Validation en el borde (controllers).
- Errores: lanzar excepciones de dominio de `shared.error`; `GlobalExceptionHandler` las traduce a RFC 9457.
- Tiempo: inyectar `java.time.Clock`; nunca `Instant.now()` directo (testeable).
- Migraciones Flyway **nunca se editan** una vez commiteadas: se agrega una nueva `V{n}__*.sql`.
- Seguridad por método con `@PreAuthorize` además de reglas en `SecurityConfig`.

## Convenciones frontend

- Angular 21 moderno: standalone components, `inject()`, **signals** (`signal`, `computed`, `resource`/`httpResource`),
  control flow `@if/@for`, `input()/output()`, zoneless, `ChangeDetectionStrategy.OnPush`.
- **Prohibido editar** `src/app/api/` (generado). Regenerar con `npm run generate:api`.
- Features lazy en `src/app/features/<feature>`; transversales en `src/app/core`.
- Textos de UI en español. Código en inglés.

## Comandos

```bash
# Infra local
docker compose up -d db                          # Solo Postgres
docker compose up --build                        # Todo el stack

# Backend (desde backend/)
./mvnw verify                                    # Tests unitarios + integración + módulos + contrato (requiere Docker)
./mvnw spring-boot:test-run                      # API en :8080 con Postgres en Testcontainers (perfil local)

# Frontend (desde frontend/)
npm run generate:api                             # Regenera cliente desde specs/api/openapi.yaml
npm start                                        # :4200 con proxy /api → :8080
npm test                                         # Vitest
```

## Qué NO hacer

- No guardar tokens en `localStorage` (ver ADR-0004).
- No exponer entidades JPA en controllers.
- No introducir dependencias nuevas sin justificarlo en el commit.
- No commitear secretos: usar variables de entorno (`.env.example` documenta cuáles).
