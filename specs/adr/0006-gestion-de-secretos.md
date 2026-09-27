# ADR-0006 · Gestión de secretos con Azure Key Vault y Managed Identity

- **Estado:** Aceptado
- **Fecha:** 2026-09-27

## Contexto

La app necesita cuatro secretos: la clave de firma JWT, la contraseña de PostgreSQL, la contraseña del admin y la de
los participantes demo. La primera versión del despliegue generaba los secretos al azar, pero:

- los guardaba **en texto plano** en un archivo local (`.env.azure`);
- los pasaba como **argumentos de la línea de comandos** a `az`, visibles en la lista de procesos;
- no tenía **auditoría** ni una **rotación** centralizada;
- el repositorio incluía un **secreto JWT de desarrollo** en `.env.example` y en `application-local.yml`.

## Decisión

**Azure Key Vault** (modo RBAC) es la única fuente de verdad de los secretos en la nube.

| Aspecto | Cómo se resuelve |
|---|---|
| Generación | `infra/azure/deploy.sh` genera cada secreto **solo si no existe** en el vault. El valor pasa por un archivo temporal (permisos 600, se borra al salir), nunca por argv. |
| Consumo | Los Container Apps declaran los secretos como **referencias** (`keyvaultref:<uri>,identityref:<id>`). No hay valores copiados en la configuración. |
| Acceso al vault | **Identidad administrada** (*user-assigned*) con el rol `Key Vault Secrets User`. Sin credenciales para acceder al vault. La misma identidad tiene `AcrPull` para descargar imágenes. |
| Personas | Quien despliega tiene `Key Vault Secrets Officer`. Las credenciales para compartir se leen con `az keyvault secret show`. |
| Rotación | Se cambia el valor en el vault y se reinicia la revisión. Para PostgreSQL, el script rota la contraseña del servidor cuando el secreto es nuevo. |
| Local | `.env.example` no trae valores; `scripts/init-env.sh` genera un `.env` aleatorio. El perfil `local` usa una clave JWT **efímera** si no hay `JWT_SECRET`. Fuera de ese perfil, la app **no arranca** sin un secreto válido. |

## Alternativas consideradas

- **Secretos directos en Container Apps:** están cifrados en reposo, pero sin auditoría ni rotación central, y se
  copian en cada despliegue.
- **GitHub Actions secrets + despliegue desde CI:** es válido, pero mueve la fuente de verdad a GitHub, y la app en
  Azure seguiría necesitando los valores copiados.
- **PostgreSQL sin contraseña (Entra ID + Managed Identity):** elimina el secreto de la base de datos. Es el
  **siguiente paso** natural; se pospone porque requiere el plugin de autenticación de Azure para JDBC y configurar la
  identidad como administrador de Entra en el servidor.

## Consecuencias

- (+) No hay secretos en el repo, en archivos locales ni en la línea de comandos.
- (+) El acceso a los secretos queda auditado en Key Vault y se controla con RBAC.
- (+) Rotar un secreto no requiere redesplegar las imágenes.
- (−) Un recurso más (costo despreciable: ~USD 0,03 por 10.000 operaciones).
- (−) La primera asignación de roles RBAC tarda en propagarse; el script reintenta.
- (−) Requiere un entorno de Container Apps **estándar** (`--environment-mode WorkloadProfiles`): el modo *express*,
  que la CLI usa por defecto, no soporta referencias a Key Vault.
