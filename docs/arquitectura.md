# Arquitectura

Diagramas en notación **C4** (contexto → contenedores → componentes) y flujo de datos del caso de uso principal.
Los diagramas de componentes de cada módulo también se **generan automáticamente** desde el código con Spring Modulith
(`./mvnw test` → `backend/target/spring-modulith-docs/`), así la documentación no se desactualiza.

## Nivel 1 · Contexto

```mermaid
C4Context
    title Polla Mundialista · Contexto
    Person(user, "Participante", "Predice marcadores y consulta el ranking")
    Person(admin, "Administrador", "Registra los resultados reales. No participa.")
    System(polla, "Polla Mundialista", "Predicciones, puntuación y ranking de un grupo privado")
    Rel(user, polla, "Usa", "HTTPS")
    Rel(admin, polla, "Administra resultados", "HTTPS")
```

## Nivel 2 · Contenedores

```mermaid
C4Container
    title Polla Mundialista · Contenedores
    Person(user, "Participante / Admin")
    System_Boundary(b, "Polla Mundialista") {
        Container(web, "web", "nginx + Angular 21", "Sirve la SPA y hace de proxy de /api (mismo origen)")
        Container(api, "api", "Java 21 · Spring Boot 4", "API REST stateless. Monolito modular con eventos de dominio")
        ContainerDb(db, "db", "PostgreSQL 17", "Usuarios, partidos, predicciones, refresh tokens, event_publication")
    }
    Rel(user, web, "Navega", "HTTPS")
    Rel(web, api, "Proxy /api/v1", "HTTP/JSON")
    Rel(api, db, "Lee/escribe", "JDBC")
```

| Contenedor | Por qué esta tecnología |
|---|---|
| **web** | Angular 21 (zoneless, signals) compilado a estáticos. nginx pone la SPA y la API en el mismo origen: la cookie de refresh es *first-party* y no hay CORS en producción. |
| **api** | Spring Boot 4: Spring Security (JWT nativo con Nimbus), validación, Actuator. Spring Modulith hace verificables los límites entre módulos y persiste los eventos. |
| **db** | Relacional (requisito). `timestamptz` para horarios, `DENSE_RANK()` para el ranking, restricciones `CHECK`/`UNIQUE` como última línea de defensa. |

## Nivel 3 · Componentes del API (módulos)

```mermaid
flowchart TB
    subgraph api[api · dev.leiber.polla]
        direction TB
        shared[shared<br/><small>errores RFC 9457 · seguridad JWT · Clock</small>]
        auth[auth<br/><small>registro · login · refresh rotativo · admin bootstrap</small>]
        matches[matches<br/><small>catálogo · resultados · publica MatchResultRegistered</small>]
        predictions[predictions<br/><small>upsert · regla de cierre · PredictionLedger</small>]
        scoring[scoring<br/><small>ScoringPolicy · ScoreRecalculator</small>]
        leaderboard[leaderboard<br/><small>read model SQL: ranking · resumen · historial</small>]
    end

    predictions -->|MatchCatalog| matches
    predictions -. implementa PredictionStatistics .-> matches
    scoring -->|PredictionLedger| predictions
    matches == MatchResultRegistered ==> scoring
    leaderboard -->|SQL de solo lectura| DB[(PostgreSQL)]
```

Reglas (verificadas por `ModularityTests`):

- Cada módulo expone solo su paquete raíz (API). `internal` y `web` son privados.
- **Sin ciclos:** `matches` necesita el conteo de predicciones, pero en vez de depender de `predictions` define un puerto
  (`PredictionStatistics`) que `predictions` implementa (inversión de dependencias).
- `matches` **no conoce** a `scoring`: se comunican por un evento.

## Flujo de datos · Registrar un resultado

```mermaid
sequenceDiagram
    autonumber
    actor Admin
    participant Web as web (Angular)
    participant M as matches
    participant EPR as event_publication (outbox)
    participant S as scoring
    participant P as predictions
    participant L as leaderboard

    Admin->>Web: Registra COL 2-1 JPN y confirma
    Web->>M: PUT /admin/matches/1/result {2,1,version}
    activate M
    M->>M: Valida versión (concurrencia optimista)
    M->>M: status = FINISHED
    M->>EPR: Persiste MatchResultRegistered (misma transacción)
    M-->>Web: 200 AdminMatch
    deactivate M
    Note over EPR,S: Después del commit, de forma asíncrona
    EPR->>S: MatchResultRegistered
    S->>P: scoreMatch(1, pred -> policy.score(pred, 2-1))
    P->>P: points = 3 / 1 / 0 (sobrescribe → idempotente)
    S->>EPR: Marca el evento como completado
    Web->>L: GET /leaderboard
    L-->>Web: Ranking con DENSE_RANK
```

Si el paso 8 falla (p. ej. la BD se cae), el evento queda **incompleto** en `event_publication` y se reprocesa al
reiniciar. Como el recálculo sobrescribe los puntos, reprocesarlo es seguro.

## ¿Por qué escala?

| Dimensión | Hoy | Siguiente paso (sin reescribir) |
|---|---|---|
| **Tráfico de la API** | Backend *stateless* (JWT + refresh en BD) | N réplicas detrás de un balanceador (Azure Container Apps / Kubernetes) |
| **Lecturas del ranking** | 1 consulta agregada con índices | Proyección `leaderboard_snapshot` actualizada por el mismo evento + caché |
| **Recálculo de puntos** | Listener asíncrono en proceso con outbox | Externalizar el evento a Kafka / Azure Service Bus (soporte nativo de Spring Modulith) |
| **Extraer un servicio** | Módulos con límites verificados y comunicación por eventos | `scoring` o `leaderboard` a su propio despliegue |
| **Rate limiting** | En memoria (1 instancia) | Redis / Bucket4j distribuido, mismo contrato |
| **Frontend** | Estáticos | CDN |
| **Base de datos** | PostgreSQL gestionado | Réplicas de lectura para `leaderboard` |

**Por qué no microservicios desde el día 1:** para un grupo privado agregarían red, despliegues múltiples y
consistencia distribuida sin beneficio real. El monolito modular deja la puerta abierta con costo casi cero
([ADR-0002](../specs/adr/0002-monolito-modular.md)).
