# ADR-0008 · Acceso a datos: JPA para escribir, SQL nativo para lecturas agregadas y operaciones masivas

- **Estado:** Aceptado
- **Fecha:** 2026-10-06

## Contexto

El backend combina dos tipos de acceso a datos:

1. **Escrituras sobre entidades con reglas:** registrar un resultado (con concurrencia optimista), crear o actualizar
   una predicción, marcar una cuenta como eliminada.
2. **Lecturas agregadas y operaciones masivas:** el ranking con desempates, el historial filtrado y la purga de datos
   por lotes.

Para el segundo tipo, JPA/JPQL no tiene las herramientas necesarias:

| Necesidad | SQL de PostgreSQL | ¿JPQL? |
|---|---|---|
| Ranking con empates que comparten posición | `DENSE_RANK() OVER (ORDER BY …)` | No |
| Contar aciertos exactos y de ganador en una sola pasada | `COUNT(*) FILTER (WHERE …)` | No |
| Borrar por lotes sin esperar filas bloqueadas | `DELETE … WHERE id IN (SELECT … LIMIT n FOR UPDATE SKIP LOCKED)` | No |
| Timeout solo para una transacción | `set_config('lock_timeout', …, true)` | No |

Con JPA habría que hacer varias consultas, o cargar los datos en memoria y calcular en Java: justo lo que no escala.

## Decisión

| Tipo de acceso | Herramienta | Dónde vive |
|---|---|---|
| Escritura de entidades | **Spring Data JPA** (Hibernate) | Repositorios `JpaRepository` en `<módulo>/infrastructure` |
| Lecturas agregadas (ranking, historial) | **SQL nativo con `JdbcClient`** | `<módulo>/infrastructure/*ReadRepository` |
| Operaciones masivas (purgas) | **SQL nativo con `JdbcClient`** + `BatchDeleter` | `<módulo>/infrastructure/*PurgeRepository` |

Reglas:

- **El SQL solo vive en `infrastructure`.** La capa `application` contiene los casos de uso y las reglas de negocio
  (p. ej. "el historial ajeno solo muestra partidos cerrados") y llama a métodos con nombre de negocio
  (`findHistory`, `purgeByUser`). No conoce el SQL.
- **Parámetros siempre con nombre** (`:userId`), nunca concatenando valores del usuario: no hay riesgo de inyección SQL.
- Las consultas se escriben como **text blocks de Java** dentro del repositorio. Así quedan junto al código que las
  usa y se leen sin saltar entre archivos.
- El esquema sigue validado: `ddl-auto=validate` para JPA y pruebas de integración sobre **PostgreSQL real**
  (Testcontainers) para el SQL nativo.

Es un **CQRS liviano**: el modelo de escritura (entidades JPA) y el de lectura (consultas SQL a medida) están
separados, sobre la misma base de datos.

## Alternativas consideradas

- **Todo con JPA/JPQL:** más uniforme, pero obliga a calcular el ranking en memoria y no permite los lotes con
  `SKIP LOCKED` del ADR-0007.
- **Archivos `.sql` separados:** útiles si un equipo de base de datos revisa las consultas por separado. Aquí agregan
  una indirección sin beneficio.
- **jOOQ (SQL tipado):** detecta errores de SQL al compilar. Es el siguiente paso natural si las consultas nativas
  crecen; hoy son seis y no justifican una dependencia más.
- **Vista materializada para el ranking:** documentada en la spec 04 como ruta de escalabilidad. Hoy, una consulta con
  índices responde en milisegundos.

## Consecuencias

- (+) Cada consulta usa la herramienta adecuada, y el ranking se resuelve en una sola consulta.
- (+) Las capas se respetan: cambiar el SQL no toca los casos de uso, y viceversa.
- (−) El SQL nativo depende de PostgreSQL (funciones de ventana, `FILTER`, `SKIP LOCKED`). Es una elección consciente:
  PostgreSQL es requisito del proyecto.
- (−) Los errores de SQL se detectan en las pruebas de integración, no al compilar (mitigable con jOOQ).
