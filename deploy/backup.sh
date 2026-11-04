#!/usr/bin/env sh
# Sauvegarde de la base MySQL et des fichiers des cours (volume « uploads »).
# Chaque nuit à 3 h :  (crontab -l 2>/dev/null; echo "0 3 * * * /opt/lms/LMS/deploy/backup.sh >> /opt/lms/backup.log 2>&1") | crontab -
set -eu
cd "$(dirname "$0")"

# Lit une valeur du fichier .env sans l'exécuter (les mots de passe peuvent contenir des caractères spéciaux)
env_value() {
  value=$(grep -E "^$1=" .env 2>/dev/null | tail -n 1 | cut -d= -f2- || true)
  echo "${value:-$2}"
}

DEST=$(env_value BACKUP_DIR /opt/lms/backups)
KEEP_DAYS=$(env_value BACKUP_KEEP_DAYS 7)
STAMP=$(date +%Y-%m-%d_%H%M)
mkdir -p "$DEST"

# Base : export cohérent sans bloquer les utilisateurs (transaction), accents conservés (utf8mb4)
docker compose exec -T db sh -c \
  'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers --default-character-set=utf8mb4 lms_db' \
  | gzip > "$DEST/lms_db_$STAMP.sql.gz"
if [ ! -s "$DEST/lms_db_$STAMP.sql.gz" ] || ! gzip -t "$DEST/lms_db_$STAMP.sql.gz"; then
  echo "ERREUR : sauvegarde de la base vide ou illisible" >&2
  exit 1
fi

# Fichiers des cours (PDF, vidéos, audios, copies des étudiants)
docker run --rm -v lms_uploads:/data:ro -v "$DEST":/backup alpine:3.22 \
  tar czf "/backup/uploads_$STAMP.tar.gz" -C /data .

# Rotation : on garde KEEP_DAYS jours
find "$DEST" -name 'lms_db_*.sql.gz' -mtime +"$KEEP_DAYS" -delete
find "$DEST" -name 'uploads_*.tar.gz' -mtime +"$KEEP_DAYS" -delete

echo "$(date '+%F %T') Sauvegarde OK : $DEST/lms_db_$STAMP.sql.gz et uploads_$STAMP.tar.gz"
