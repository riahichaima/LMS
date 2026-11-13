#!/usr/bin/env sh
# Récupère une sauvegarde depuis la copie chiffrée hors serveur (faite par backup.sh avec RCLONE_REMOTE),
# par exemple sur une nouvelle VM après la perte de l'ancienne. Les fichiers sont déchiffrés à l'arrivée.
#   ./fetch-offsite.sh                      # liste les sauvegardes disponibles
#   ./fetch-offsite.sh 2026-11-20_0300      # télécharge celle-ci dans BACKUP_DIR/offsite, puis :
#   ./restore.sh <BACKUP_DIR>/offsite/lms_db_2026-11-20_0300.sql.gz <BACKUP_DIR>/offsite/uploads_2026-11-20_0300.tar.gz
set -eu
cd "$(dirname "$0")"

env_value() {
  value=$(grep -E "^$1=" .env 2>/dev/null | tail -n 1 | cut -d= -f2- || true)
  echo "${value:-$2}"
}

REMOTE=$(env_value RCLONE_REMOTE "")
[ -n "$REMOTE" ] || { echo "RCLONE_REMOTE n'est pas défini dans .env" >&2; exit 1; }
RCLONE_CONF=$(env_value RCLONE_CONFIG_DIR /opt/lms/rclone)
DEST="$(env_value BACKUP_DIR /opt/lms/backups)/offsite"

if [ $# -eq 0 ]; then
  docker run --rm -v "$RCLONE_CONF":/config/rclone rclone/rclone:1.75.2 lsl "$REMOTE" --include 'lms_db_*.sql.gz'
  exit 0
fi

STAMP="$1"
mkdir -p "$DEST"
docker run --rm -v "$RCLONE_CONF":/config/rclone -v "$DEST":/data rclone/rclone:1.75.2 \
  copy "$REMOTE" /data --include "lms_db_$STAMP.sql.gz" --include "uploads_$STAMP.tar.gz"
[ -s "$DEST/lms_db_$STAMP.sql.gz" ] || { echo "Sauvegarde $STAMP introuvable sur $REMOTE" >&2; exit 1; }
gzip -t "$DEST/lms_db_$STAMP.sql.gz"
echo "Récupéré et déchiffré dans $DEST. Pour restaurer :"
if [ -f "$DEST/uploads_$STAMP.tar.gz" ]; then
  echo "  ./restore.sh $DEST/lms_db_$STAMP.sql.gz $DEST/uploads_$STAMP.tar.gz"
else
  echo "  ./restore.sh $DEST/lms_db_$STAMP.sql.gz"
fi
