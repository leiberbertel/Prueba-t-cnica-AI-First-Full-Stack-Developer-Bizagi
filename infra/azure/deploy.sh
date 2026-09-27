#!/usr/bin/env bash
# Despliegue en Azure: Container Apps (web + api) + PostgreSQL Flexible Server + Container Registry + Key Vault.
#
#   az login
#   ./infra/azure/deploy.sh            # primera vez: crea todo. Siguientes: reconstruye y actualiza imágenes.
#
# Topología:
#   Internet ──HTTPS──▶ web (nginx + Angular, ingress externo) ──HTTP /api──▶ api (Spring Boot, ingress interno)
#                                                                               ├──TLS──▶ PostgreSQL Flexible
#                                                   Managed Identity ─RBAC──▶  └──────▶ Key Vault (secretos)
#
# Gestión de secretos (ADR-0006):
#   - Los secretos se generan y viven SOLO en Key Vault. No hay secretos en el repo ni en archivos locales.
#   - Las apps los leen por referencia (keyvaultref) con una identidad administrada: sin contraseñas para el vault.
#   - Al generarlos se escriben en archivos temporales (permisos 600) que se borran al terminar; nunca pasan
#     como argumentos de la línea de comandos.
#   - Para leer un secreto (p. ej. para compartir credenciales):
#       az keyvault secret show --vault-name <vault> -n admin-password --query value -o tsv
set -euo pipefail
# Git Bash (Windows) convierte argumentos que empiezan con "/" en rutas de Windows y rompe los IDs de Azure
# (/subscriptions/...). Se desactiva esa conversión.
export MSYS_NO_PATHCONV=1

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
NAMES_FILE="$ROOT/.azure-resources"   # solo nombres de recursos, sin secretos (ignorado por git)
cd "$ROOT"

# Azure for Students solo permite: mexicocentral, spaincentral, westus, francecentral, belgiumcentral
LOCATION="${LOCATION:-mexicocentral}"
RG="${RG:-rg-polla-mundialista}"
ENV_NAME="polla-env"
API_APP="polla-api"
WEB_APP="polla-web"
IDENTITY="polla-identity"
TAG="$(git rev-parse --short HEAD)$(git diff --quiet HEAD -- . || echo -dirty)"
ADMIN_EMAIL="admin@polla.local"

# Azure CLI en Windows es un programa nativo: necesita rutas de archivo de Windows (C:\...), no de Git Bash (/tmp/...).
winpath() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi; }
log() { printf '\n\033[1;32m▶ %s\033[0m\n' "$*"; }
rand() { python -c "import secrets,string;a=string.ascii_letters+string.digits;print(''.join(secrets.choice(a) for _ in range($1)),end='')"; }

SECRETS_DIR="$(mktemp -d)"
chmod 700 "$SECRETS_DIR"
trap 'rm -rf "$SECRETS_DIR"' EXIT

# ── Nombres únicos (no son secretos) ─────────────────────────────────────────────────────────────
if [[ ! -f "$NAMES_FILE" ]]; then
  suffix="$(rand 6 | tr '[:upper:]' '[:lower:]')"
  printf 'ACR_NAME=pollaacr%s\nPG_SERVER=polla-pg-%s\nKV_NAME=polla-kv-%s\n' "$suffix" "$suffix" "$suffix" > "$NAMES_FILE"
fi
# shellcheck disable=SC1090
source "$NAMES_FILE"

# ── Infraestructura base (idempotente) ───────────────────────────────────────────────────────────
log "Grupo de recursos $RG ($LOCATION)"
az group create -n "$RG" -l "$LOCATION" -o none

# La extensión containerapp expone --environment-mode (la CLI base no).
az extension add --name containerapp --upgrade --only-show-errors -y

for provider in Microsoft.App Microsoft.OperationalInsights Microsoft.ContainerRegistry \
                Microsoft.DBforPostgreSQL Microsoft.KeyVault Microsoft.ManagedIdentity; do
  az provider register -n "$provider" --wait -o none
done

if ! az acr show -n "$ACR_NAME" -g "$RG" -o none 2>/dev/null; then
  log "Container Registry $ACR_NAME"
  az acr create -n "$ACR_NAME" -g "$RG" --sku Basic -o none
fi
ACR_ID="$(az acr show -n "$ACR_NAME" -g "$RG" --query id -o tsv)"
REGISTRY="$(az acr show -n "$ACR_NAME" -g "$RG" --query loginServer -o tsv)"

# ── Key Vault (RBAC) ────────────────────────────────────────────────────────────────────────────
if ! az keyvault show -n "$KV_NAME" -g "$RG" -o none 2>/dev/null; then
  log "Key Vault $KV_NAME"
  az keyvault create -n "$KV_NAME" -g "$RG" -l "$LOCATION" --enable-rbac-authorization true -o none
fi
KV_ID="$(az keyvault show -n "$KV_NAME" -g "$RG" --query id -o tsv)"
KV_URI="$(az keyvault show -n "$KV_NAME" -g "$RG" --query properties.vaultUri -o tsv)"
KV_URI="${KV_URI%/}"

# Quien despliega puede administrar secretos (necesario para generarlos y leerlos).
ME="$(az ad signed-in-user show --query id -o tsv)"
az role assignment create --assignee-object-id "$ME" --assignee-principal-type User \
  --role "Key Vault Secrets Officer" --scope "$KV_ID" -o none 2>/dev/null || true

# Crea el secreto solo si no existe. El valor pasa por un archivo temporal, nunca por argv.
# Reintenta mientras se propaga el rol RBAC recién asignado.
ensure_secret() {
  local name="$1" generator="$2"
  if az keyvault secret show --vault-name "$KV_NAME" -n "$name" -o none 2>/dev/null; then
    return 0
  fi
  local file="$SECRETS_DIR/$name"
  (umask 077 && eval "$generator" > "$file")
  local error=""
  for attempt in $(seq 1 12); do
    if error="$(az keyvault secret set --vault-name "$KV_NAME" -n "$name" --file "$(winpath "$file")"         --encoding utf-8 -o none 2>&1)"; then
      log "Secreto '$name' generado en Key Vault"
      CREATED_SECRETS+=("$name")
      return 0
    fi
    echo "  Intento $attempt: aún sin acceso al vault (propagación RBAC), reintentando..." >&2
    sleep 10
  done
  echo "No se pudo guardar el secreto '$name' en Key Vault. Último error:" >&2
  echo "$error" >&2
  exit 1
}
# Escribe el valor de un secreto a un archivo temporal (para comandos de az que lo necesitan con @archivo).
secret_to_file() {
  local file="$SECRETS_DIR/$1.out"
  (umask 077 && az keyvault secret show --vault-name "$KV_NAME" -n "$1" --query value -o tsv | tr -d '\r\n' > "$file")
  winpath "$file"
}

CREATED_SECRETS=()
ensure_secret jwt-secret     "python -c \"import os,base64;print(base64.b64encode(os.urandom(48)).decode(),end='')\""
ensure_secret db-password    "rand 32"
ensure_secret admin-password "printf 'Admin-%s' \"\$(rand 12)\""
ensure_secret demo-password  "printf 'Demo-%s' \"\$(rand 10)\""

# ── PostgreSQL ──────────────────────────────────────────────────────────────────────────────────
DB_PASSWORD_FILE="$(secret_to_file db-password)"
if ! az postgres flexible-server show -n "$PG_SERVER" -g "$RG" -o none 2>/dev/null; then
  log "PostgreSQL Flexible Server $PG_SERVER (Burstable B1ms, ~5 min)"
  az postgres flexible-server create -n "$PG_SERVER" -g "$RG" -l "$LOCATION" \
    --tier Burstable --sku-name Standard_B1ms --storage-size 32 --version 17 \
    --admin-user pollaadmin --admin-password "@$DB_PASSWORD_FILE" \
    --public-access 0.0.0.0 --yes -o none
elif [[ " ${CREATED_SECRETS[*]} " == *" db-password "* ]]; then
  log "Rotando la contraseña de PostgreSQL con el nuevo secreto del vault"
  az postgres flexible-server update -n "$PG_SERVER" -g "$RG" --admin-password "@$DB_PASSWORD_FILE" -o none
fi
if ! az postgres flexible-server db show -s "$PG_SERVER" -g "$RG" --name polla -o none 2>/dev/null; then
  log "Base de datos polla"
  az postgres flexible-server db create -s "$PG_SERVER" -g "$RG" --name polla -o none
fi
PG_HOST="$(az postgres flexible-server show -n "$PG_SERVER" -g "$RG" --query fullyQualifiedDomainName -o tsv)"

# ── Identidad administrada: lee secretos del vault y descarga imágenes del registro ──────────────
if ! az identity show -n "$IDENTITY" -g "$RG" -o none 2>/dev/null; then
  log "Identidad administrada $IDENTITY"
  az identity create -n "$IDENTITY" -g "$RG" -l "$LOCATION" -o none
fi
IDENTITY_ID="$(az identity show -n "$IDENTITY" -g "$RG" --query id -o tsv)"
IDENTITY_PRINCIPAL="$(az identity show -n "$IDENTITY" -g "$RG" --query principalId -o tsv)"
az role assignment create --assignee-object-id "$IDENTITY_PRINCIPAL" --assignee-principal-type ServicePrincipal \
  --role "Key Vault Secrets User" --scope "$KV_ID" -o none 2>/dev/null || true
az role assignment create --assignee-object-id "$IDENTITY_PRINCIPAL" --assignee-principal-type ServicePrincipal \
  --role AcrPull --scope "$ACR_ID" -o none 2>/dev/null || true

if ! az containerapp env show -n "$ENV_NAME" -g "$RG" -o none 2>/dev/null; then
  log "Container Apps Environment $ENV_NAME"
  # Entorno estándar (NO "express"). Sin --environment-mode, la CLI actual crea entornos express, que no soportan
  # referencias a Key Vault ni el descubrimiento interno de servicios que usa web → api.
  # https://learn.microsoft.com/azure/container-apps/express-overview
  az containerapp env create -n "$ENV_NAME" -g "$RG" -l "$LOCATION" --environment-mode WorkloadProfiles -o none
fi

# ── Imágenes: build local + push al registro ────────────────────────────────────────────────────
log "Build y push de imágenes ($TAG) a $REGISTRY"
az acr login -n "$ACR_NAME"
docker build -f backend/Dockerfile -t "$REGISTRY/polla-api:$TAG" .
docker build -t "$REGISTRY/polla-web:$TAG" frontend
docker push "$REGISTRY/polla-api:$TAG"
docker push "$REGISTRY/polla-web:$TAG"

# ── API (ingress interno: no es accesible desde internet) ───────────────────────────────────────
# Los secretos son REFERENCIAS al vault, no valores.
API_SECRETS=()
for name in jwt-secret db-password admin-password demo-password; do
  API_SECRETS+=("$name=keyvaultref:$KV_URI/secrets/$name,identityref:$IDENTITY_ID")
done
API_ENV=(
  "DB_URL=jdbc:postgresql://$PG_HOST:5432/polla?sslmode=require"
  "DB_USERNAME=pollaadmin"
  "DB_PASSWORD=secretref:db-password"
  "JWT_SECRET=secretref:jwt-secret"
  "ADMIN_EMAIL=$ADMIN_EMAIL"
  "ADMIN_PASSWORD=secretref:admin-password"
  "DEMO_DATA=true"
  "DEMO_PASSWORD=secretref:demo-password"
  "COOKIE_SECURE=true"
)
if ! az containerapp show -n "$API_APP" -g "$RG" -o none 2>/dev/null; then
  log "Creando $API_APP"
  az containerapp create -n "$API_APP" -g "$RG" --environment "$ENV_NAME" \
    --image "$REGISTRY/polla-api:$TAG" --user-assigned "$IDENTITY_ID" \
    --registry-server "$REGISTRY" --registry-identity "$IDENTITY_ID" \
    --ingress internal --target-port 8080 --transport http \
    --cpu 0.5 --memory 1.0Gi --min-replicas 1 --max-replicas 2 \
    --secrets "${API_SECRETS[@]}" --env-vars "${API_ENV[@]}" -o none
else
  log "Actualizando $API_APP"
  az containerapp identity assign -n "$API_APP" -g "$RG" --user-assigned "$IDENTITY_ID" -o none
  az containerapp registry set -n "$API_APP" -g "$RG" --server "$REGISTRY" --identity "$IDENTITY_ID" -o none
  az containerapp secret set -n "$API_APP" -g "$RG" --secrets "${API_SECRETS[@]}" -o none
  az containerapp update -n "$API_APP" -g "$RG" --image "$REGISTRY/polla-api:$TAG" \
    --set-env-vars "${API_ENV[@]}" -o none
fi

# ── Web (ingress externo con HTTPS gestionado por Azure) ────────────────────────────────────────
if ! az containerapp show -n "$WEB_APP" -g "$RG" -o none 2>/dev/null; then
  log "Creando $WEB_APP"
  az containerapp create -n "$WEB_APP" -g "$RG" --environment "$ENV_NAME" \
    --image "$REGISTRY/polla-web:$TAG" --user-assigned "$IDENTITY_ID" \
    --registry-server "$REGISTRY" --registry-identity "$IDENTITY_ID" \
    --ingress external --target-port 80 \
    --cpu 0.25 --memory 0.5Gi --min-replicas 1 --max-replicas 2 \
    --env-vars "API_UPSTREAM=http://$API_APP" "TRUST_EDGE_PROXY=true" -o none
else
  log "Actualizando $WEB_APP"
  az containerapp identity assign -n "$WEB_APP" -g "$RG" --user-assigned "$IDENTITY_ID" -o none
  az containerapp registry set -n "$WEB_APP" -g "$RG" --server "$REGISTRY" --identity "$IDENTITY_ID" -o none
  # TRUST_EDGE_PROXY=true: nginx está detrás del borde de Azure (Envoy), que agrega la IP real del cliente.
  az containerapp update -n "$WEB_APP" -g "$RG" --image "$REGISTRY/polla-web:$TAG" \
    --set-env-vars "API_UPSTREAM=http://$API_APP" "TRUST_EDGE_PROXY=true" -o none
fi

URL="https://$(az containerapp show -n "$WEB_APP" -g "$RG" --query properties.configuration.ingress.fqdn -o tsv)"
log "Listo: $URL"
cat <<EOF
Credenciales (se leen del Key Vault, no se guardan en disco):
  Admin: $ADMIN_EMAIL
    az keyvault secret show --vault-name $KV_NAME -n admin-password --query value -o tsv
  Usuarios demo: ana@polla.local, carlos@polla.local, valentina@polla.local, diego@polla.local
    az keyvault secret show --vault-name $KV_NAME -n demo-password --query value -o tsv
EOF
