# AI_LOG · Desarrollo AI-First

## Cómo trabajo con la IA

| Herramienta | Uso |
|---|---|
| **Claude Code** (Claude Opus 5.5) | Agente principal: diseño, implementación, pruebas, despliegue y diagnóstico |
| **MCP del Angular CLI** (`.mcp.json`) | El agente consulta las buenas prácticas oficiales de Angular 21 en lugar de suponerlas |
| **Nano Banana** (Gemini) | Ilustración del login |

**División de responsabilidades.** Yo defino el alcance y las reglas de negocio, tomo las decisiones de arquitectura y
seguridad, y **no acepto nada sin evidencia**. La IA propone, implementa, diagnostica y documenta dentro de límites que
fijé por escrito:

- **Spec-Driven Development:** primero specs, contrato OpenAPI y ADRs (`specs/`), después el código. Cada criterio de
  aceptación (`CA-xx`) tiene su prueba.
- **[`CLAUDE.md`](../CLAUDE.md):** reglas del repo para cualquier agente, por ejemplo: capas por módulo, migraciones
  inmutables, nada de tokens en `localStorage`, commits solo con mi aprobación.
- **Compuertas automáticas:** prueba de límites entre módulos, prueba de contrato contra el OpenAPI, CI en cada push.

**Mis tres preguntas de revisión.** A cada propuesta de la IA le aplico: *¿qué pasa si esto se filtra?*, *¿qué pasa con
un millón de registros?* y *¿cómo lo abusaría un atacante?* Los casos de abajo salieron de esas preguntas.

---

## Prompt 1 · Arquitectura antes que código

**Contexto:** quería que la IA partiera de un contrato revisable y no "inventara" el sistema sobre la marcha.

```text
Actúa como arquitecto. Antes de escribir código, genera en specs/:
- 00-vision.md con alcance, fuera de alcance explícito, glosario, reglas de negocio numeradas (RN-xx)
  y requisitos no funcionales;
- una spec por módulo (auth, predicciones, admin, ranking) con historias de usuario y criterios de aceptación
  Dado/Cuando/Entonces numerados (CA-xx) y casos borde;
- api/openapi.yaml como fuente de verdad del contrato (errores RFC 9457);
- ADRs para: stack, monolito modular vs microservicios, recálculo de puntos, estrategia de tokens y contract-first.
Restricciones: el cierre de predicciones lo valida el servidor; recalcular puntos debe ser idempotente;
nada de JWT en localStorage. No escribas código hasta que revise las specs.
```

**Mi criterio:** al revisar la spec identifiqué un **conflicto de interés** que no estaba contemplado: el admin, que
registra los resultados reales, también podía competir. Lo convertí en la regla **RN-09** (el admin no participa),
**primero en la spec** y después en el código y las pruebas (`CA-02.9`). El historial de commits muestra ese orden.

---

## Prompt 2 · Revisión de escalabilidad: eliminar una cuenta

**Contexto:** la primera implementación de "Eliminar mi cuenta" (HU-01.6) usaba `ON DELETE CASCADE` en una sola
transacción. Funcionaba y pasaba las pruebas, pero la revisé con mi segunda pregunta:

```text
Si un usuario tiene un millón de registros asociados, la operación puede generar un lock al borrar en
cascada. Esto podría tumbar la DB
```

**Qué aportó la IA:** un análisis contra el modelo de datos que confirmó tres riesgos:

1. Los refresh tokens **crecían sin límite**: uno por cada renovación de sesión, sin purga.
2. Un `CASCADE` masivo es una transacción larga: bloqueos, pico de WAL, y en un servidor de 1 vCPU degrada a todos.
3. El `CASCADE` hacía que `auth` borrara datos de `predictions`, **saltándose los límites entre módulos**.

**Mi decisión:** diseño escalable completo, con una condición explícita: **timeouts en todas las capas, porque lo que
falla rápido no tumba la base de datos**. Registrado en [ADR-0007](../specs/adr/0007-eliminacion-asincrona-de-cuentas.md):

| Pieza | Qué hace |
|---|---|
| Fase síncrona (≤ 5 s) | Marca `deleted_at`, **anonimiza al instante** (Ley 1581), revoca sesiones y publica el evento en el outbox → `202 Accepted` |
| Fase asíncrona | Cada módulo borra **sus propios** datos por lotes (`FOR UPDATE SKIP LOCKED`), una transacción corta por lote |
| Timeouts | Por lote `lock_timeout` 2 s y `statement_timeout` 10 s; globales 5 s y 15 s; pool 5 s |
| Integridad | `CASCADE` → `RESTRICT`: la base de datos impide borrar un usuario con datos pendientes |

**Evidencia:** una prueba bloquea a propósito la fila del usuario desde otra conexión. La purga **falla en menos de 5 s**
en lugar de esperar, y se completa al liberar el bloqueo.

**Precisión conceptual:** no es un *soft delete*. `deleted_at` es solo un estado de tránsito; es un **borrado físico
diferido**. Un soft delete conservaría datos personales y no cumpliría el derecho de supresión.

---

## Prompt 3 · Auditoría de producción: el rate limit se podía evadir

**Contexto:** al revisar `COOKIE_SECURE: false` en `docker-compose.yml` pedí una auditoría completa de lo que debe cambia
entre desarrollo y producción, con mi tercera pregunta: *¿cómo lo abusaría un atacante?*

```text
Deben activarse las restricciones de cookie porque ya estamos en producción
```

**Qué aportó la IA:** confirmó que `COOKIE_SECURE` era correcto (el compose es solo local) y encontró una
vulnerabilidad real. Spring tomaba el **primer** valor de `X-Forwarded-For`, que escribe el cliente, así que cambiando
una IP falsa en cada intento se evadía el límite de 10 logins por minuto. También faltaba HSTS.

**Mi criterio:** primero **demostrar** la vulnerabilidad y después corregirla. Un test sobre un servidor real (MockMvc
no pasa por Tomcat) simula al atacante:

| | Intento 11 |
|---|---|
| Antes (`forward-headers-strategy: framework`) | `401`: el límite se evade |
| Después (`native` + `RemoteIpValve`, que lee de derecha a izquierda saltando proxies de confianza) | `429` |

Al repetir el ataque a través de nginx en Docker apareció un segundo caso. Ahí nginx es el borde, así que se agregó
`TRUST_EDGE_PROXY`: como borde, nginx descarta el encabezado del cliente; detrás de Azure, respeta la cadena que
construye el borde. Resultado: `429` en ambos entornos, más HSTS.

---

## Prompt 4 · Despliegue continuo sin contraseñas

**Contexto:** desplegaba a mano con `deploy.sh` desde mi equipo. Producción podía quedar desalineada con `main`, o
recibir código que no pasó el CI.

```text
Implementemos un despliegue automático al mezclar los cambios exitosamente con la rama main
por medio de GitHub Action, usando como autenticación OIDC
```

**Qué aportó la IA:** el diseño concreto sobre mi pedido:

- Una **identidad administrada** dedicada al despliegue, con una credencial federada que solo confía en `main`.
- Imágenes etiquetadas con el commit, para trazabilidad exacta.
- `concurrency` para evitar dos despliegues a la vez.
- Un *smoke test* que hace fallar el job si la API no responde.

**Mi criterio:** OIDC y no el secreto de un *service principal*, por el mismo principio del ADR-0006. Además, una
identidad propia con **permisos mínimos**: puede publicar imágenes y actualizar las dos apps, pero no tiene acceso a
PostgreSQL ni a Key Vault. Pedí ver los comandos de Azure antes de ejecutarlos.

**El primer despliegue falló** al iniciar sesión en Azure con `AADSTS700213`. El error mostró que GitHub firma el token
con los **IDs inmutables** del dueño y del repo (`repo:owner@id/repo@id:...`), y la credencial esperaba el formato sin
IDs. Corregí la credencial y lo documenté en el [ADR-0009](../specs/adr/0009-despliegue-continuo.md). El formato nuevo
además es más seguro: un repo borrado y recreado con el mismo nombre ya no coincide.

**Evidencia:** la segunda ejecución quedó en verde (pruebas, build, despliegue y *smoke test*), y producción corre la
imagen `954bdac`, el commit exacto.

---

## Bloqueo resuelto con IA · Azure rechazaba las referencias a Key Vault

**Contexto:** decidí que los secretos vivieran solo en **Azure Key Vault** y que los Container Apps los leyeran por
referencia con una identidad administrada ([ADR-0006](../specs/adr/0006-gestion-de-secretos.md)). Al crear la API,
Azure respondió:

```text
ExpressEnvironmentFeatureNotSupported: 'KeyVaultUrl in secrets' is not supported ... on express environments
```

Recrear el entorno con `--enable-workload-profiles true` no lo resolvió. En vez de probar variantes a ciegas, pedí
diagnosticarlo con fuentes:

```text
Creé el entorno con --enable-workload-profiles true y sigue diciendo que es express.
¿Cómo decide Azure el modo del entorno? Busca en la documentación oficial y verifica el valor real en el recurso.
```

**Diagnóstico:**

- La documentación de *Container Apps express* confirmó que ese modo no soporta referencias a Key Vault ni el
  descubrimiento interno de servicios (web → api).
- La API *preview* de Azure mostró el valor real del recurso: `environmentMode: Express`.
- El modo se controla con `--environment-mode`, disponible solo en la extensión `containerapp`.

**Resolución:** `--environment-mode WorkloadProfiles` quedó fijado en [`deploy.sh`](../infra/azure/deploy.sh), con un
comentario del porqué y la consecuencia documentada en el ADR-0006. La arquitectura de secretos se mantuvo intacta:
no se relajó la seguridad para sortear el bloqueo.

---

## Otros problemas detectados y resueltos

Cada fila se detectó con una verificación explícita (pruebas, `curl`, navegador o logs), no por casualidad.

| Problema | Cómo se detectó | Resolución |
|---|---|---|
| Tipos SQL (`smallint`, `char`) incompatibles con las entidades | `ddl-auto=validate` + pruebas sobre PostgreSQL real (Testcontainers) | Migración ajustada antes de publicarse. Con H2 no se habría notado |
| Cabeceras de seguridad ausentes en nginx | `curl -I` al stack dockerizado | Herencia de `add_header` en nginx; caché con `expires` y comentario preventivo |
| Estilos sin aplicar en producción | Comparación visual local vs. producción | La optimización `inlineCritical` de Angular usa JS en línea que **nuestra CSP bloquea**; se desactivó la optimización en lugar de relajar la CSP |
| Pipeline fallando en CI | Log de GitHub Actions (`Permission denied`) | `mvnw` y scripts sin permiso de ejecución al versionarse desde Windows |
| Regiones bloqueadas en Azure for Students | Error de política al desplegar | Consulta de la política y selección de Mexico Central (menor latencia a Colombia) |
| IDs de Azure alterados en Git Bash | Error real mostrado por el script | `MSYS_NO_PATHCONV` y `cygpath`; el script ahora falla con el error real |
| Mensajes de error genéricos en operaciones sin cuerpo | Prueba del diálogo de eliminar cuenta | El Problem Detail llegaba como texto; `problemOf` lo interpreta y tiene pruebas |
| Error 401 en consola al entrar sin sesión | Revisión de la consola del navegador | "Sin sesión" no es un error: `/auth/refresh` sin cookie responde `204` (CA-01.7) |
| Navegación colgada tras el logout | Prueba en navegador | Se quitaron las *View Transitions*: un detalle estético no justifica ese riesgo en una demo en vivo |

---

## Imágenes con Nano Banana

**Iteraciones:** 1. El prompt ([`docs/imagenes.md`](imagenes.md)) ya incluía las restricciones de la interfaz (paleta
`#0b3d2e` / `#f2c94c`, formato vertical, tercio inferior oscuro para el título) y las legales (sin logos, marcas,
camisetas reales ni personas reconocibles). El resultado se evaluó contra esos criterios, no "a gusto".

**Integración:** JPG de 628 KB → **WebP de 62 KB**. Encuadre calculado para que el balón quede sobre el título en
escritorio (`center bottom`) y visible en la franja móvil (`--auth-hero-y`). Si la imagen falla, queda como respaldo
la cancha dibujada con CSS. Sin cambios en la CSP (mismo origen).

---

## Qué no delegué

- **Alcance y reglas de negocio:** RN-09 (el admin no participa), RN-10 (el admin no se puede eliminar), visibilidad de
  predicciones ajenas, desempates.
- **Seguridad:** dónde vive cada token (ADR-0004), secretos solo en Key Vault (ADR-0006), la auditoría de producción,
  despliegue con OIDC y mínimo privilegio (ADR-0009).
- **Escalabilidad:** exigir borrado por lotes con timeouts (ADR-0007).
- **Plataforma:** mantener Azure ante los bloqueos en vez de migrar de proveedor.
- **Criterio de terminado:** nada se dio por hecho sin evidencia. Backend (52 pruebas), frontend (26), CI en verde,
  pruebas en navegador y *smoke tests* contra la URL pública de Azure, leyendo las credenciales del vault sin
  imprimirlas.
