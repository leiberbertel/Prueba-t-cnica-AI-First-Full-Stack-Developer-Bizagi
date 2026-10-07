#!/usr/bin/env bash
# Restablece la base de datos de PRODUCCIÓN (Azure) a los datos de la demo.
#
#   ./infra/azure/reset-demo-data.sh
#
# Qué hace:
#   1. Abre el firewall de PostgreSQL solo para tu IP (temporal; se cierra siempre al salir, aunque algo falle).
#   2. Vacía usuarios, predicciones, sesiones y eventos, y deja los 12 partidos sin resultado.
#      No toca equipos, partidos ni el historial de migraciones.
#   3. Reinicia la API: al arrancar recrea el admin y los 4 participantes demo con sus predicciones
#      (semilla fija: siempre los mismos datos). Las contraseñas vienen de Key Vault y no cambian.
#
# DESTRUCTIVO: pide escribir el nombre del servidor para confirmar (o CONFIRM=<servidor> para uso no interactivo).
set -euo pipefail
export MSYS_NO_PATHCONV=1 # Git Bash no debe convertir los IDs de Azure (/subscriptions/...)

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
# shellcheck disable=SC1091
source "$ROOT/.azure-resources"
RG="${RG:-rg-polla-mundialista}"
API_APP="polla-api"
DB_USER="pollaadmin"
RULE="reset-demo-$(date +%s)"

log() { printf '\n\033[1;32m▶ %s\033[0m\n' "$*"; }
winpath() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi; }

# ── Confirmación ────────────────────────────────────────────────────────────────────────────────
echo "Vas a BORRAR los usuarios, predicciones y resultados de PRODUCCIÓN ($PG_SERVER)."
if [[ "${CONFIRM:-}" != "$PG_SERVER" ]]; then
  read -r -p "Escribe el nombre del servidor para confirmar: " answer
  [[ "$answer" == "$PG_SERVER" ]] || { echo "Cancelado."; exit 1; }
fi

WORK="$(mktemp -d)"
chmod 700 "$WORK"
cleanup() {
  az postgres flexible-server firewall-rule delete -g "$RG" -s "$PG_SERVER" -n "$RULE" --yes -o none \
    2>/dev/null && echo "Firewall cerrado ($RULE)."
  rm -rf "$WORK"
}
trap cleanup EXIT

# ── Acceso temporal ─────────────────────────────────────────────────────────────────────────────
MY_IP="$(curl -s https://api.ipify.org)"
log "Abriendo el firewall solo para $MY_IP"
az postgres flexible-server firewall-rule create -g "$RG" -s "$PG_SERVER" -n "$RULE" \
  --start-ip-address "$MY_IP" --end-ip-address "$MY_IP" -o none

PG_HOST="$(az postgres flexible-server show -g "$RG" -n "$PG_SERVER" --query fullyQualifiedDomainName -o tsv)"
# La contraseña pasa por un archivo temporal (permisos 600), nunca por la línea de comandos.
(umask 077 && printf 'PGPASSWORD=%s\n' \
  "$(az keyvault secret show --vault-name "$KV_NAME" -n db-password --query value -o tsv | tr -d '\r\n')" \
  > "$WORK/pg.env")

psql_prod() {
  docker run --rm -i --env-file "$(winpath "$WORK/pg.env")" postgres:17-alpine \
    psql "host=$PG_HOST port=5432 dbname=polla user=$DB_USER sslmode=require" -v ON_ERROR_STOP=1 -qAt "$@"
}

# ── Reset ───────────────────────────────────────────────────────────────────────────────────────
log "Vaciando datos (una sola transacción)"
psql_prod <<'SQL'
BEGIN;
TRUNCATE predictions, refresh_tokens, users, event_publication RESTART IDENTITY;
UPDATE matches SET status = 'SCHEDULED', home_goals = NULL, away_goals = NULL,
                   result_registered_at = NULL, version = 0;
COMMIT;
SQL

log "Reiniciando $API_APP para que recree el admin y los datos demo"
REVISION="$(az containerapp revision list -n "$API_APP" -g "$RG" --query "[?properties.active].name | [0]" -o tsv)"
az containerapp revision restart -n "$API_APP" -g "$RG" --revision "$REVISION" -o none

for attempt in $(seq 1 30); do
  users="$(psql_prod -c "select count(*) from users" 2>/dev/null || echo 0)"
  [[ "$users" -ge 5 ]] && break
  sleep 10
done

log "Estado final"
psql_prod <<'SQL'
select 'usuarios: '   || count(*) || ' (admin: ' || count(*) filter (where role = 'ADMIN') || ')' from users;
select 'predicciones: ' || count(*) from predictions;
select 'partidos con resultado: ' || count(*) from matches where status = 'FINISHED';
SQL
