# ADR-0001 · Stack tecnológico

- **Estado:** Aceptado
- **Fecha:** 2026-09-27

## Contexto

Aplicación fullstack con autenticación, roles, reglas de negocio con puntuación y ranking. Se evalúan arquitectura,
calidad y seguridad, y puede haber **cambios en vivo** durante la presentación: el stack debe ser uno que el equipo
domine a profundidad para poder razonar y modificar el código con seguridad (con o sin IA).

## Decisión

| Capa | Tecnología | Motivo |
|---|---|---|
| Backend | **Java 21 + Spring Boot 4.1** | LTS, *records*, *pattern matching*, virtual threads. Ecosistema maduro para seguridad y datos. |
| Modularidad | **Spring Modulith** | Límites entre módulos verificados por test + eventos de dominio persistidos (ADR-0002, ADR-0003). |
| Seguridad | **Spring Security + OAuth2 Resource Server (JWT)** | JWT con Nimbus (incluido en Spring), sin librerías de terceros ni filtros manuales. |
| Persistencia | **PostgreSQL 17 + Spring Data JPA + Flyway** | Relacional (requisito), `timestamptz`, funciones de ventana para el ranking, migraciones versionadas. |
| Frontend | **Angular 21** (standalone, *zoneless*, Signals) | Framework completo y opinado: router, DI, formularios, HTTP e i18n sin ensamblar piezas. |
| UI | **Angular Material 3** con tema propio | Componentes accesibles (WCAG) mantenidos por el equipo de Angular. |
| Contrato | **OpenAPI 3** + cliente generado (`ng-openapi-gen`) | Contract-first (ADR-0005). |
| Pruebas | JUnit 5, Testcontainers, Vitest, Playwright | Unitarias, integración con Postgres real y e2e. |
| Entrega | Docker + Docker Compose, GitHub Actions | Un comando para levantar todo; CI en cada push. |

## Alternativas consideradas

- **.NET 9 + Angular:** igualmente válido. Se descartó por dominio del equipo, dado el riesgo de cambios en vivo.
- **NestJS (Node) fullstack TypeScript:** un solo lenguaje, pero el modelo de seguridad y la modularidad de
  Spring (Security + Modulith) aportan más garantías "de fábrica" para lo que se evalúa.
- **React:** más libre, pero obliga a elegir router, estado y formularios. Angular trae decisiones consistentes.

## Consecuencias

- (+) Seguridad y límites de módulos verificables con pruebas automáticas.
- (+) Dos lenguajes tipados de extremo a extremo; el contrato OpenAPI evita desalineaciones.
- (−) La JVM consume más memoria que Node en planes de hosting gratuitos (mitigado con un JRE *slim* y límites de heap).
