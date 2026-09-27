# Esquema de base de datos

PostgreSQL 17. El esquema se versiona con **Flyway** en
`backend/src/main/resources/db/migration`. Este documento es la vista de diseño; las migraciones son la verdad ejecutable.

```mermaid
erDiagram
    USERS ||--o{ PREDICTIONS : hace
    USERS ||--o{ REFRESH_TOKENS : posee
    TEAMS ||--o{ MATCHES : "local"
    TEAMS ||--o{ MATCHES : "visitante"
    MATCHES ||--o{ PREDICTIONS : recibe

    USERS {
        bigint id PK
        varchar email UK "normalizado a minúsculas"
        varchar display_name
        varchar password_hash "BCrypt"
        varchar role "USER | ADMIN"
        timestamptz created_at
        timestamptz deleted_at "null = activa (ADR-0007)"
    }
    REFRESH_TOKENS {
        bigint id PK
        bigint user_id FK
        varchar token_hash UK "SHA-256 hex"
        timestamptz expires_at
        timestamptz revoked_at "null = activo"
        timestamptz created_at
    }
    TEAMS {
        bigint id PK
        varchar code UK "COL, ARG..."
        varchar name
        varchar flag_code "ISO alpha-2"
        varchar group_code
    }
    MATCHES {
        bigint id PK
        varchar group_code
        int matchday "1..3"
        bigint home_team_id FK
        bigint away_team_id FK
        timestamptz kickoff_at
        varchar venue
        varchar status "SCHEDULED | FINISHED"
        int home_goals "null hasta resultado"
        int away_goals
        timestamptz result_registered_at
        bigint version "concurrencia optimista"
    }
    PREDICTIONS {
        bigint id PK
        bigint user_id FK
        bigint match_id FK
        int home_goals
        int away_goals
        int points "null hasta resultado"
        timestamptz created_at
        timestamptz updated_at
    }
```

## Restricciones e índices

| Tabla | Restricción / índice | Motivo |
|---|---|---|
| `users` | `UNIQUE (email)` (se guarda en minúsculas) | Email único sin importar mayúsculas |
| `users` | `CHECK (role IN ('USER','ADMIN'))` | Integridad del rol |
| `refresh_tokens` | `UNIQUE (token_hash)`, índice en `user_id` | Búsqueda por hash y revocación masiva |
| `refresh_tokens` | Índice en `expires_at` | Purga periódica de tokens vencidos, por lotes |
| `users` | Índice parcial en `deleted_at` (solo cuentas eliminadas) | Encontrar cuentas pendientes de purga |
| `predictions`, `refresh_tokens` | FK a `users` con **`ON DELETE RESTRICT`** | Cada módulo borra sus datos por lotes; la BD impide borrar un usuario con datos pendientes (ADR-0007) |
| `matches` | `CHECK (home_team_id <> away_team_id)` | Un equipo no juega contra sí mismo |
| `matches` | `CHECK` goles 0–20 y ambos null o ambos no null | Resultado coherente |
| `matches` | Índice en `kickoff_at` | Listado ordenado |
| `predictions` | `UNIQUE (user_id, match_id)` | RN-01 y protección ante requests concurrentes |
| `predictions` | Índice en `match_id` | Recálculo por partido |
| `predictions` | `CHECK` goles 0–20, `points` 0–3 o null | Integridad |

La tabla `event_publication` la crea Spring Modulith (registro de eventos, ver ADR-0003).
