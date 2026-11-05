#!/usr/bin/env sh
# Mise à jour du serveur : fichiers de déploiement à jour (git) + dernières images publiées par la CD.
# Retour arrière : dans .env, mettre BACKEND_TAG / AI_TAG sur le tag d'une ancienne version, puis relancer ce script.
set -eu
cd "$(dirname "$0")"

avant=$(git rev-parse HEAD)
git pull --ff-only
docker compose pull
docker compose up -d --remove-orphans --wait --wait-timeout 600
# Caddyfile monté comme fichier : git le remplace, le conteneur garderait l'ancien → recréer Caddy s'il a changé
if ! git diff --quiet "$avant" HEAD -- Caddyfile && docker compose ps --services | grep -qx caddy; then
  docker compose up -d --force-recreate --no-deps caddy
fi
docker image prune -f >/dev/null
docker compose ps
