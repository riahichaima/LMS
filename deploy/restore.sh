#!/usr/bin/env sh
# Restauration d'une sauvegarde faite par backup.sh.
#   ./restore.sh /opt/lms/backups/lms_db_2026-11-20_0300.sql.gz                 # base seulement
#   ./restore.sh /opt/lms/backups/lms_db_2026-11-20_0300.sql.gz /opt/lms/backups/uploads_2026-11-20_0300.tar.gz
set -eu
cd "$(dirname "$0")"

DB_FILE="${1:?Indiquer le fichier de sauvegarde de la base (.sql.gz)}"
UPLOADS_FILE="${2:-}"
[ -f "$DB_FILE" ] || { echo "Fichier introuvable : $DB_FILE" >&2; exit 1; }
gzip -t "$DB_FILE" || { echo "Sauvegarde illisible : $DB_FILE" >&2; exit 1; }
if [ -n "$UPLOADS_FILE" ]; then
  [ -f "$UPLOADS_FILE" ] || { echo "Fichier introuvable : $UPLOADS_FILE" >&2; exit 1; }
fi

printf 'Les données actuelles seront REMPLACÉES par %s. Taper OUI pour continuer : ' "$DB_FILE"
read -r answer
[ "$answer" = "OUI" ] || { echo "Annulé."; exit 1; }

# Plus aucune écriture pendant la restauration
docker compose stop backend

gunzip -c "$DB_FILE" | docker compose exec -T db sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 lms_db'

if [ -n "$UPLOADS_FILE" ]; then
  dir=$(cd "$(dirname "$UPLOADS_FILE")" && pwd)
  name=$(basename "$UPLOADS_FILE")
  docker run --rm -v lms_uploads:/data -v "$dir":/backup:ro alpine:3.22 \
    sh -c "find /data -mindepth 1 -delete && tar xzf \"/backup/$name\" -C /data"
fi

docker compose start backend
echo "Restauration terminée. Vérifier : docker compose ps"
