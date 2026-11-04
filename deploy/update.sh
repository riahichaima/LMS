#!/usr/bin/env sh
# Mise à jour du serveur : fichiers de déploiement à jour (git) + dernières images publiées par la CD.
# Retour arrière : dans .env, mettre BACKEND_TAG / AI_TAG sur le tag d'une ancienne version, puis relancer ce script.
set -eu
cd "$(dirname "$0")"

git pull --ff-only
docker compose pull
docker compose up -d --remove-orphans --wait --wait-timeout 600
docker image prune -f >/dev/null
docker compose ps
