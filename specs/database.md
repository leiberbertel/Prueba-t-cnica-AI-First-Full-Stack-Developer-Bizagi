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
    }
    REFRESH_TOKENS {
        bigint id PK
        bigint user_id FK
        char token_hash UK "SHA-256 hex"
        timestamptz expires_at
        timestamptz revoked_at "null = activo"
        timestamptz created_at
    }
    TEAMS {
        bigint id PK
        varchar code UK "COL, ARG..."
        varchar name
        char flag_code "ISO alpha-2"
        char group_code
    }
    MATCHES {
        bigint id PK
        char group_code
        smallint matchday "1..3"
        bigint home_team_id FK
        bigint away_team_id FK
        timestamptz kickoff_at
        varchar venue
        varchar status "SCHEDULED | FINISHED"
        smallint home_goals "null hasta resultado"
        smallint away_goals
        timestamptz result_registered_at
        bigint version "concurrencia optimista"
    }
    PREDICTIONS {
        bigint id PK
        bigint user_id FK
        bigint match_id FK
        smallint home_goals
        smallint away_goals
        smallint points "null hasta resultado"
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
| `matches` | `CHECK (home_team_id <> away_team_id)` | Un equipo no juega contra sí mismo |
| `matches` | `CHECK` goles 0–20 y ambos null o ambos no null | Resultado coherente |
| `matches` | Índice en `kickoff_at` | Listado ordenado |
| `predictions` | `UNIQUE (user_id, match_id)` | RN-01 y protección ante requests concurrentes |
| `predictions` | Índice en `match_id` | Recálculo por partido |
| `predictions` | `CHECK` goles 0–20, `points` 0–3 o null | Integridad |

La tabla `event_publication` la crea Spring Modulith (registro de eventos, ver ADR-0003).
