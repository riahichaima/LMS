#!/bin/sh
# Génère assets/env.js au démarrage du conteneur à partir de la variable API_URL :
# la même image fonctionne sur n'importe quel serveur.
set -eu

API_URL="${API_URL:-http://localhost:8090}"
API_URL="${API_URL%/}"

case "$API_URL" in
  http://*|https://*) ;;
  *) echo "40-lms-env.sh : API_URL invalide (doit commencer par http:// ou https://) : $API_URL" >&2; exit 1 ;;
esac
# Pas de caractères capables de casser le JavaScript généré
if printf '%s' "$API_URL" | grep -q "[\"'<>\\\\ ]"; then
  echo "40-lms-env.sh : API_URL contient des caractères interdits" >&2
  exit 1
fi

printf 'window.__env = { apiUrl: "%s" };\n' "$API_URL" > /usr/share/nginx/html/assets/env.js
echo "40-lms-env.sh : API du LMS = $API_URL"
