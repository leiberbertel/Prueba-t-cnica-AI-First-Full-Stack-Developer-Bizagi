# ADR-0002 · Monolito modular (y no microservicios)

- **Estado:** Aceptado
- **Fecha:** 2026-09-27

## Contexto

Cuatro capacidades de negocio (auth, partidos/predicciones, puntuación, ranking) para un grupo privado. Se valora
que la arquitectura sea **escalable**, pero microservicios desde el día 1 agregan red, despliegues, consistencia
eventual y observabilidad distribuida sin un beneficio real a este tamaño.

## Decisión

Un **monolito modular** con Spring Modulith. Cada módulo es un paquete de primer nivel. Su paquete raíz es la
**API pública** (interfaces, eventos, vistas) y dentro tiene las mismas capas, privadas para los demás módulos:

```
dev.leiber.polla
├── shared        → utilidades transversales (errores RFC 9457, seguridad JWT, reloj)
├── auth          → cuentas de usuario, roles, registro, login, tokens JWT y refresh
├── matches       → equipos, partidos, resultados (publica MatchResultRegistered)
├── predictions   → predicciones de usuarios (reglas de cierre)
├── scoring       → ScoringPolicy + recálculo (escucha MatchResultRegistered)
├── leaderboard   → ranking, resumen e historiales (consultas de lectura)
└── demo          → datos de demostración (opcional)

<módulo>
├── (raíz)          → API pública: lo único que otros módulos pueden usar
├── domain/         → entidades y reglas de negocio
├── application/    → casos de uso, listeners de eventos, consultas
├── infrastructure/ → repositorios, configuración, seeders
└── web/            → controllers y DTOs
```

Un test (`ApplicationModules.of(...).verify()`) **falla el build** si un módulo accede a las capas internas de otro o
si aparece una dependencia cíclica.

Los módulos se comunican de dos formas:

- **Consultas síncronas** a la API pública de otro módulo (p. ej. `predictions` → `MatchCatalog`).
- **Eventos de dominio** para reaccionar a cambios sin acoplarse (p. ej. `matches` → `MatchResultRegistered` → `scoring`).
  El emisor no conoce a sus consumidores.

## Por qué escala

1. **Horizontal:** el backend es *stateless* (JWT + refresh en BD), así que se pueden poner N instancias detrás de un balanceador.
2. **Lecturas:** el ranking es una consulta agregada con índices. Si crece, se reemplaza por una proyección materializada
   actualizada por eventos, sin cambiar el contrato HTTP.
3. **Extracción de servicios:** las reacciones entre módulos van por **eventos de dominio** y las consultas por
   interfaces públicas pequeñas. Si `scoring` necesitara escalar aparte, se externaliza el evento (Spring Modulith lo
   soporta hacia Kafka, RabbitMQ o Azure Service Bus), la interfaz `PredictionLedger` pasa a ser una llamada HTTP, y el
   módulo se despliega por separado **sin reescribir la lógica de negocio**.
4. **Frontend:** estático, servido por CDN.

## Consecuencias

- (+) Un solo despliegue, transacciones locales y depuración simple.
- (+) Límites de módulo explícitos y verificados, el prerrequisito para una eventual migración a microservicios.
- (−) Todos los módulos escalan juntos hasta que se extraiga alguno.
