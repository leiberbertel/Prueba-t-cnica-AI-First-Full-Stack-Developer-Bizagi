# AI_LOG · Desarrollo AI-First

> **Herramientas:** Claude Code (modelo Claude Opus 5.5) como agente principal de desarrollo, con el servidor MCP del
> Angular CLI conectado (`.mcp.json`) para que el agente consulte las buenas prácticas oficiales de Angular 21.
> Nano Banana (Gemini) para las imágenes.
>
> **Método:** Spec-Driven Development. Primero las specs, el contrato OpenAPI y los ADRs (`specs/`); después el código,
> con pruebas que referencian cada criterio de aceptación (`CA-xx`). `CLAUDE.md` fija las reglas del repo para
> cualquier agente. El historial de commits refleja ese orden.

---

## 1 · Prompt de arranque: specs antes que código

**Objetivo:** que la IA no "inventara" el sistema, sino que partiera de un contrato revisable.

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

**Resultado y criterio humano:** al revisar la spec detecté un **conflicto de interés** que la IA no había considerado:
el admin, que carga los resultados, también podía competir. Lo convertí en la regla **RN-09** (el admin no participa)
**primero en la spec** (commit `docs(specs): admin does not participate…`) y después en el código y las pruebas (`CA-02.9`).

---

## 2 · Bloqueo: Hibernate rechazaba el esquema de Flyway

**Síntoma:** 26 de 40 pruebas de integración fallaban al arrancar el contexto.

```text
Schema validation: wrong column type encountered in column [away_goals] in table [matches];
found [int2 (Types#SMALLINT)], but expecting [integer (Types#INTEGER)]
```

**Prompt:**

```text
Las pruebas de integración fallan al crear el EntityManagerFactory con este error [pego el stack].
Tengo ddl-auto=validate a propósito: no quiero que Hibernate modifique el esquema.
¿Cambio las entidades o la migración? La migración V1 aún no se ha commiteado ni desplegado.
```

**Cómo ayudó la IA:** explicó que Hibernate 7 mapea `Integer` a `integer` y `String` a `varchar`, así que `smallint` y
`char(n)` no validan. Propuso dos caminos: `@JdbcTypeCode` en cada campo, o cambiar la migración. Como la migración
**no estaba publicada**, era seguro editarla (regla del repo: *una migración commiteada nunca se edita*). Al corregir
apareció el segundo caso (`char(1)` → `bpchar`), y lo resolvimos igual.

**Aprendizaje:** `ddl-auto=validate` junto con pruebas sobre **Postgres real** (Testcontainers) detectó el desajuste
antes de llegar a producción. Con H2 en memoria habría pasado desapercibido.

---

## 3 · Bloqueo: las cabeceras de seguridad "desaparecían" en nginx

**Síntoma:** el `docker compose` levantaba bien y el login funcionaba, pero `curl -I` no mostraba el CSP ni
`X-Frame-Options`, aunque estaban declarados en el bloque `server`.

**Prompt:**

```text
En nginx declaré add_header de seguridad a nivel server {}, pero la respuesta de / no los trae.
Te paso el default.conf.template completo. ¿Por qué no se aplican?
```

**Cómo ayudó la IA:** identificó la trampa de herencia de nginx: si un `location` declara **cualquier** `add_header`
(yo tenía `Cache-Control`), deja de heredar **todos** los `add_header` del nivel superior. La solución fue controlar la
caché con la directiva `expires` (que no usa `add_header`) y dejar un comentario en el archivo para que nadie reintroduzca
el problema. Lo verificamos con `curl -I` en la SPA y en los assets.

---

## 4 · Bloqueo: el logout no redirigía (verificación en navegador real)

**Síntoma:** al probar la app en el navegador, el logout respondía `204`, la sesión se limpiaba, pero la ruta seguía en
`/partidos`.

**Diagnóstico con la IA:** revisando el estado desde el navegador (`document.visibilityState`, la URL, las peticiones de
red), la IA relacionó el problema con `withViewTransitions()`: la navegación espera a que la *View Transition* pinte
frames, y en una ventana que no se está renderizando nunca avanza. **Decisión:** quitar las view transitions. Son un
detalle estético que no justifica el riesgo de una navegación colgada durante una demo en vivo.

---

## 5 · Criterio humano: "¿el script tiene credenciales quemadas?"

**Contexto:** el primer script de despliegue generaba los secretos al azar, pero los guardaba en texto plano en un
archivo local y los pasaba como argumentos a `az`. Además, `.env.example` traía un secreto JWT de desarrollo.

**Prompt (mío, al revisar el script):**

```text
¿Azure tiene un servicio donde podemos colocar los secretos, usemos ese. Veo que el .sh que creaste tiene credenciales quemadas.
```

**Resultado:** la IA aclaró que no había valores fijos (se generaban al azar), pero reconoció las debilidades reales:
texto plano en disco, secretos visibles en la lista de procesos, sin auditoría ni rotación, y un secreto de desarrollo
en el repo. Propuso **Azure Key Vault + Managed Identity**:

- los secretos se generan directamente en el vault;
- los Container Apps los leen **por referencia**;
- ningún secreto queda en el repo, en disco ni en argv.

Lo documentamos en [ADR-0006](../specs/adr/0006-gestion-de-secretos.md).

**Aprendizaje:** revisar lo que genera la IA con la pregunta "¿qué pasaría si esto se filtra?" llevó a un diseño más
seguro que el que la IA propuso al principio.

---

## 6 · Bloqueos del despliegue en Azure

Tres fallos seguidos, cada uno con una causa distinta. La IA los diagnosticó leyendo el error real y la documentación
oficial, en lugar de adivinar.

| # | Síntoma | Causa | Solución |
|---|---|---|---|
| 1 | `RequestDisallowedByAzure` al crear el registro | La suscripción solo permite 5 regiones (política *Allowed resource deployment regions*) | Consultar la política con `az policy assignment list`, verificar la disponibilidad de servicios y usar **Mexico Central** (la más cercana a Colombia) |
| 2 | No se podía guardar el secreto en Key Vault, sin mensaje de error | Git Bash convierte los argumentos que empiezan con `/` (los IDs `/subscriptions/...`) en rutas de Windows, así que la asignación de rol RBAC fallaba en silencio. Después, `az` (programa nativo) no encontraba archivos en `/tmp/...` | `MSYS_NO_PATHCONV=1` para los IDs y `cygpath -w` para los archivos. El script ahora **muestra el error real** en lugar de silenciarlo |
| 3 | `ExpressEnvironmentFeatureNotSupported: KeyVaultUrl in secrets` | La CLI actual crea los entornos de Container Apps en modo **express**, que no soporta referencias a Key Vault ni el descubrimiento interno de servicios (web → api). `--enable-workload-profiles` **no** cambia el modo | La API *preview* mostraba `environmentMode: Express`. Con la extensión `containerapp` se usa `--environment-mode WorkloadProfiles` |

**Prompt clave (fallo 3):**

```text
Creé el entorno con --enable-workload-profiles true y sigue diciendo que es express.
¿Cómo decide Azure el modo del entorno? Busca en la documentación oficial y verifica el valor real en el recurso.
```

**Aprendizaje:** el fallo 2 lo empeoró mi propio script, que tenía `2>/dev/null || true`: un error silenciado cuesta
más tiempo que uno visible. Ahora el script falla rápido y muestra la causa.

---

## 7 · Imágenes con Nano Banana

**Herramienta:** Nano Banana (Gemini). **Iteraciones:** 1. El primer resultado cumplió todos los criterios.

**Prompt** (completo en [`docs/imagenes.md`](imagenes.md)): estadio nocturno a nivel de cancha, balón en primer
plano con luz dorada, confeti dorado, paleta de la app (`#0b3d2e`, `#f2c94c`), **sin texto, logos, marcas, personas
reconocibles ni camisetas reales**, formato vertical con el tercio inferior oscuro para el texto superpuesto.

**Por qué se aceptó a la primera:** el prompt se escribió con las restricciones de la UI ya definidas (paleta, zona
oscura para el título, formato del panel) y con las restricciones legales explícitas (nada de marcas oficiales del
Mundial). Así, el resultado se evaluó contra criterios concretos y no "a gusto".

**Integración (con ayuda de la IA):**

| Paso | Decisión |
|---|---|
| Formato | JPG de 628 KB → **WebP de 62 KB** (calidad 82), 10 veces más liviano sin pérdida visible. El original se guarda en [`docs/assets/`](assets/hero-nano-banana-original.jpg) y no se despliega. |
| Encuadre en escritorio | Con `center` el balón quedaba detrás del logo y del título. Se calculó la posición del balón según la escala `cover` y se ancló la imagen abajo (`center bottom`): el balón queda en 226–389 px y el texto empieza en 452 px. |
| Encuadre en móvil | La franja de 200 px mostraba solo pasto. Una variable CSS (`--auth-hero-y: 62%`) encuadra el balón (79–164 px) sin tocar las demás capas del fondo. |
| Respaldo | Si la imagen no carga, se mantiene la cancha dibujada con CSS de las capas inferiores. |
| Seguridad | La imagen se sirve desde el mismo origen: la CSP (`img-src 'self'`) no necesitó cambios. |

---

## 8 · Criterio humano: "¿y si el usuario tiene un millón de registros?"

**Contexto:** la primera versión de "Eliminar mi cuenta" (HU-01.6) borraba la fila del usuario y dejaba que PostgreSQL
eliminara predicciones y sesiones con `ON DELETE CASCADE`, en una sola transacción. Pasaba todas las pruebas.

**Mi pregunta al revisar el código:**

```text
¿Qué pasa si el usuario tiene un millón de registros asociados? ¿La operación no generaría un lock al borrar en
cascada? ¿Me podrían tumbar la DB?
```

**Análisis de la IA (verificado contra el modelo de datos):**

- En este dominio las predicciones están **acotadas** (máximo una por partido), **pero** los refresh tokens crecían
  **sin límite**: cada renovación de sesión creaba una fila y nunca se borraban. Era un problema real que no se había
  detectado.
- En general, un `CASCADE` masivo es **una transacción larga**: bloquea cada fila borrada, deja la petición colgada,
  genera un pico de WAL y trabajo de *vacuum*, y en un servidor B1ms (1 vCPU con créditos) degrada a todos los usuarios.
- Además, el `CASCADE` **violaba los límites entre módulos**: `auth` terminaba borrando datos de `predictions`, y el
  comentario del evento prometía un desacople que el diseño no cumplía.

**Decisión (mía):** diseño escalable completo, **con timeouts** para que cualquier operación bloqueada falle rápido en
lugar de acumular esperas. Registrado en [ADR-0007](../specs/adr/0007-eliminacion-asincrona-de-cuentas.md):

| Pieza | Qué hace |
|---|---|
| Fase síncrona (≤ 5 s) | `deleted_at`, **anonimización inmediata** de email y nombre, revocación de sesiones, evento en el outbox → `202 Accepted` |
| Fase asíncrona | Cada módulo borra **sus propios** datos por lotes: `DELETE … LIMIT n FOR UPDATE SKIP LOCKED`, una transacción corta por lote |
| Timeouts | Por lote: `lock_timeout` 2 s y `statement_timeout` 10 s. Globales: 5 s y 15 s. Pool: 5 s |
| Reintentos | Si un lote falla, el evento queda incompleto en el outbox y una tarea programada lo reenvía |
| Integridad | `CASCADE` → `RESTRICT`: la base de datos impide borrar un usuario con datos pendientes |
| Limpieza | Purga periódica de refresh tokens vencidos |

**Prueba que demuestra el "fallar rápido":** un test bloquea a propósito la fila del usuario desde otra conexión
(`SELECT … FOR UPDATE`) y ejecuta la purga. Resultado: **falla en menos de 5 s** en lugar de quedarse esperando, y al
liberar el bloqueo la purga se completa.

**Pregunta de seguimiento: "¿esto es un soft delete?"** No exactamente. Un soft delete conserva los datos
indefinidamente y se puede revertir. Aquí la marca `deleted_at` es solo un **estado de tránsito**: los datos personales
se anonimizan al instante y todo se **borra físicamente** después. Es un *borrado físico diferido*. Un soft delete
clásico no cumpliría el derecho de supresión de la Ley 1581.

**Dos bugs que aparecieron al verificar:**

1. **Mensajes de error perdidos:** en operaciones sin cuerpo de respuesta, el cliente generado pide la respuesta como
   texto, así que el Problem Detail llegaba como JSON en un string y la UI mostraba "Algo salió mal" en vez de "La
   contraseña no es correcta". Lo detectó una prueba del diálogo; se corrigió `problemOf` y se agregaron pruebas.
2. **Verificación engañosa:** al reiniciar el backend de desarrollo, detener la tarea de Maven **no mató el proceso
   Java** que había lanzado. El backend viejo siguió respondiendo "UP" en el puerto 8080 y el nuevo no arrancó. Se
   descubrió al revisar el log (`Port 8080 was already in use`) antes de dar la verificación por buena.

**Aprendizaje:** que las pruebas pasen no significa que el diseño escale. La pregunta "¿qué pasa con un millón de
registros?" descubrió un crecimiento sin límite y una violación de los límites entre módulos que ninguna prueba
funcional habría detectado.

---

## 9 · Auditoría de configuración de producción: IP falsificable en el rate limit

**Mi pregunta:** al ver `COOKIE_SECURE: false` en `docker-compose.yml` pregunté si había más configuraciones que
debían cambiar en producción.

**Hallazgo de la IA:** `COOKIE_SECURE` estaba bien (el compose es solo local), pero encontró una vulnerabilidad real.
Con `forward-headers-strategy: framework`, Spring tomaba el **primer** valor de `X-Forwarded-For`, que escribe el
cliente. Mandando una IP falsa distinta en cada intento, un atacante podía **evadir el límite de 10 logins por minuto**.
También faltaba HSTS.

**Cómo se verificó (antes de corregir):** un test sobre un **servidor real** (MockMvc no pasa por Tomcat) simula al
atacante. Con la configuración anterior, el intento 11 obtuvo `401` en vez de `429`: la vulnerabilidad quedó
demostrada con evidencia, no supuesta.

**Corrección:**

| Capa | Cambio |
|---|---|
| API | `forward-headers-strategy: native` → `RemoteIpValve` de Tomcat lee la cadena de **derecha a izquierda** saltando proxies de confianza. La IP la aporta el borde, no el cliente |
| nginx | Variable `TRUST_EDGE_PROXY`. Si nginx es el borde (local, valor por defecto), **descarta** el encabezado del cliente. Detrás de Azure (Envoy), agrega a la cadena del borde |
| nginx | Se conserva el `https` original en `X-Forwarded-Proto`, y se agrega HSTS |

**Un detalle que la primera corrección no cubría:** en Docker local el ataque **seguía funcionando**, porque la IP del
navegador llega como IP privada de Docker y la API la tomaba por un proxy propio. Por eso nginx necesita saber si es el
borde (`TRUST_EDGE_PROXY`). Después del ajuste, el ataque vía nginx local recibe `429` en el intento 11.

---

## Qué NO delegué a la IA

- **Reglas de negocio y alcance:** la revisión de la spec (RN-09, visibilidad de predicciones ajenas, desempates).
- **Decisiones de seguridad:** dónde vive cada token y por qué (ADR-0004), y exigir que los secretos salieran del
  repo y del disco (ADR-0006).
- **Decisiones de plataforma:** mantener Azure en lugar de migrar a otro proveedor cuando aparecieron los bloqueos.
- **Escalabilidad:** cuestionar el borrado en cascada y exigir procesamiento por lotes con timeouts (ADR-0007).
- **Verificación:** nada se dio por terminado sin evidencia. Suite backend (49 pruebas), frontend (22), prueba manual en
  navegador, *smoke test* del stack dockerizado con `curl`, y *smoke test* de la URL pública en Azure (login, refresh,
  403 para no-admin, cabeceras de seguridad), leyendo las credenciales del vault sin imprimirlas.
