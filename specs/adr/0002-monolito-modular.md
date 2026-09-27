# ADR-0002 · Monolito modular (y no microservicios)

- **Estado:** Aceptado
- **Fecha:** 2026-09-27

## Contexto

Cuatro capacidades de negocio (auth, partidos/predicciones, puntuación, ranking) para un grupo privado. Se valora
que la arquitectura sea **escalable**, pero microservicios desde el día 1 agregan red, despliegues, consistencia
eventual y observabilidad distribuida sin un beneficio real a este tamaño.

## Decisión

Un **monolito modular** con Spring Modulith. Cada módulo es un paquete de primer nivel con API pública mínima
y detalles internos en subpaquetes (`internal`), no accesibles desde otros módulos:

```
dev.leiber.polla
├── shared        → utilidades transversales (errores RFC 9457, reloj, config)
├── users         → cuentas de usuario y roles
├── auth          → registro, login, tokens JWT y refresh
├── matches       → equipos, partidos, resultados (publica MatchResultRegistered)
├── predictions   → predicciones de usuarios (reglas de cierre)
├── scoring       → ScoringPolicy + recálculo (escucha MatchResultRegistered)
└── leaderboard   → ranking, resumen e historiales (consultas de lectura)
```

Un test (`ApplicationModules.of(...).verify()`) **falla el build** si un módulo accede a los internos de otro o si
aparece una dependencia cíclica.

## Por qué escala

1. **Horizontal:** el backend es *stateless* (JWT + refresh en BD), así que se pueden poner N instancias detrás de un balanceador.
2. **Lecturas:** el ranking es una consulta agregada con índices. Si crece, se reemplaza por una proyección materializada
   actualizada por eventos, sin cambiar el contrato HTTP.
3. **Extracción de servicios:** los módulos se comunican por **eventos de dominio**, no por llamadas directas.
   Si `scoring` necesitara escalar aparte, se externaliza el evento (Spring Modulith lo soporta hacia Kafka, RabbitMQ o
   Azure Service Bus) y el módulo se despliega por separado **sin reescribir la lógica**.
4. **Frontend:** estático, servido por CDN.

## Consecuencias

- (+) Un solo despliegue, transacciones locales y depuración simple.
- (+) Límites de módulo explícitos y verificados, el prerrequisito para una eventual migración a microservicios.
- (−) Todos los módulos escalan juntos hasta que se extraiga alguno.
