#!/usr/bin/env bash
# Crea .env a partir de .env.example con secretos aleatorios (solo para correr el stack localmente).
#   ./scripts/init-env.sh
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -f .env ]]; then
  echo ".env ya existe; no se sobrescribe. Bórralo si quieres regenerarlo." >&2
  exit 1
fi

rand() { python -c "import secrets,string;a=string.ascii_letters+string.digits;print(''.join(secrets.choice(a) for _ in range($1)))"; }
jwt="$(python -c "import os,base64;print(base64.b64encode(os.urandom(48)).decode())")"
admin="Admin-$(rand 10)"
demo="Demo-$(rand 8)"
db="$(rand 24)"

umask 077
sed -e "s#^JWT_SECRET=.*#JWT_SECRET=$jwt#" \
    -e "s#^ADMIN_PASSWORD=.*#ADMIN_PASSWORD=$admin#" \
    -e "s#^DEMO_PASSWORD=.*#DEMO_PASSWORD=$demo#" \
    -e "s#^DB_PASSWORD=.*#DB_PASSWORD=$db#" \
    .env.example > .env

echo ".env creado con secretos aleatorios. Credenciales para entrar a http://localhost:4000:"
echo "  Admin:  admin@polla.local / (ADMIN_PASSWORD en .env)"
echo "  Demo:   ana@polla.local   / (DEMO_PASSWORD en .env)"
