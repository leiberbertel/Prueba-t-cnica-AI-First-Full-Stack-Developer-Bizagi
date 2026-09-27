# ADR-0001 · Stack tecnológico

- **Estado:** Aceptado
- **Fecha:** 2026-09-27

## Contexto

Aplicación fullstack con autenticación y roles, reglas de negocio con puntuación, un ranking con desempates y un
panel de administración. Los requisitos no funcionales que más pesan en la elección son:

- **Seguridad** de autenticación y autorización sin construirla a mano.
- **Límites claros entre módulos**, verificables automáticamente, para que el sistema pueda crecer o dividirse.
- **Consistencia** al recalcular puntos cuando cambia un resultado.
- **Un contrato explícito** entre frontend y backend.

## Decisión

| Capa | Tecnología | Motivo |
|---|---|---|
| Backend | **Java 21 + Spring Boot 4.1** | LTS, *records*, *pattern matching*, virtual threads. Ecosistema maduro para seguridad y datos. |
| Modularidad | **Spring Modulith** | Límites entre módulos verificados por un test y eventos de dominio persistidos (patrón outbox) sin infraestructura extra (ADR-0002, ADR-0003). |
| Seguridad | **Spring Security + OAuth2 Resource Server (JWT)** | JWT con Nimbus (incluido en Spring), sin librerías de terceros ni filtros manuales. |
| Persistencia | **PostgreSQL 17 + Spring Data JPA + Flyway** | Relacional (requisito), `timestamptz`, funciones de ventana para el ranking, migraciones versionadas. |
| Frontend | **Angular 21** (standalone, *zoneless*, Signals) | Framework completo y opinado: router, DI, formularios, HTTP e i18n sin ensamblar piezas. |
| UI | **Angular Material 3** con tema propio | Componentes accesibles (WCAG) mantenidos por el equipo de Angular. |
| Contrato | **OpenAPI 3** + cliente generado (`ng-openapi-gen`) | Contract-first (ADR-0005). |
| Pruebas | JUnit 5, Testcontainers, Vitest | Unitarias, integración contra PostgreSQL real, arquitectura (límites de módulos) y contrato. |
| Entrega | Docker, GitHub Actions, Azure Container Apps | Un comando para levantar todo en local; CI en cada push; secretos en Key Vault (ADR-0006). |

## Alternativas consideradas

- **.NET 10 + ASP.NET Core:** alternativa equivalente en madurez, rendimiento y seguridad, y con integración nativa con
  Azure. La modularidad verificable (Modulith) y el registro de eventos persistidos que trae Spring "de fábrica"
  requerirían más ensamblaje (p. ej. pruebas de arquitectura con NetArchTest y un outbox propio). Gracias al diseño
  *contract-first*, el backend se puede reemplazar sin tocar el frontend.
- **NestJS (Node) fullstack TypeScript:** un solo lenguaje de punta a punta, pero la modularidad y la seguridad dependen
  más de convenciones que de verificaciones automáticas.
- **React:** más flexible, pero obliga a elegir router, estado y formularios por separado. Angular trae decisiones
  consistentes y un modelo reactivo moderno (Signals).

## Consecuencias

- (+) Seguridad y límites de módulos verificables con pruebas automáticas.
- (+) Tipado de extremo a extremo; el contrato OpenAPI evita desalineaciones entre front y back.
- (−) La JVM consume más memoria que Node o .NET en instancias pequeñas (mitigado con un JRE *slim* y
  `MaxRAMPercentage`).
- (−) No hay pruebas end-to-end de navegador; la cobertura de flujos completos se apoya en las pruebas de integración
  del backend y las de componentes del frontend.
