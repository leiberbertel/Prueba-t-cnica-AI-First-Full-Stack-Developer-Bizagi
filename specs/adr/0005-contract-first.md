# ADR-0005 · Contract-first con OpenAPI

- **Estado:** Aceptado
- **Fecha:** 2026-09-27

## Contexto

Con SDD y desarrollo asistido por IA, el mayor riesgo es que front y back "inventen" formas distintas para los mismos
datos. Hace falta una fuente de verdad que ambos lados y los agentes de IA consuman.

## Decisión

- `specs/api/openapi.yaml` se escribe **antes** del código y se versiona con él.
- **Frontend:** los modelos y servicios HTTP se **generan** con `ng-openapi-gen` (`npm run generate:api`).
  Nunca se editan a mano.
- **Backend:** los DTOs se implementan siguiendo el contrato y una **prueba de contrato** compara la especificación con
  los endpoints expuestos, así que el build falla si divergen.
- Swagger UI publica el contrato para exploración manual.

## Consecuencias

- (+) Cambiar la API obliga a cambiar primero la spec. Ese flujo queda registrado en los commits.
- (+) Los agentes de IA reciben un contrato preciso como contexto, lo que reduce las alucinaciones de endpoints o campos.
- (−) Un paso extra (regenerar el cliente) al cambiar la API.
