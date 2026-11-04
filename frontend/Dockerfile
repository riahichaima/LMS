# syntax=docker/dockerfile:1.7
# Frontend LMS (Angular 22) — compilation Node puis service statique par nginx (sans root).

# ── 1. Compilation ──────────────────────────────────────────────
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
# Les tests unitaires tournent dans la CI : ici on compile seulement (ng build, sans le prebuild npm)
RUN npx ng build --configuration production

# ── 2. Service statique ────────────────────────────────────────
FROM nginxinc/nginx-unprivileged:1.29-alpine
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY docker/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --chmod=755 docker/40-lms-env.sh /docker-entrypoint.d/40-lms-env.sh
COPY --from=build /app/dist/coreui-free-angular-admin-template/browser /usr/share/nginx/html
# Seul env.js est réécrit au démarrage (adresse de l'API) : le reste du site reste en lecture seule
USER root
# Correctifs de sécurité des paquets système (l'image nginx de base peut dater de plusieurs semaines)
RUN apk upgrade --no-cache  && chown 101:101 /usr/share/nginx/html/assets/env.js
USER 101

ENV API_URL=http://localhost:8090
EXPOSE 8080
