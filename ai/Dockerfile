# syntax=docker/dockerfile:1.7
# Service IA du LMS (FastAPI + Azure AI Foundry) — utilisateur non root, dépendances épinglées.
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    UVICORN_WORKERS=2

RUN groupadd --system lms && useradd --system --gid lms --home-dir /app lms
WORKDIR /app

# Dépendances d'abord : couche mise en cache tant que requirements.txt ne change pas
COPY requirements.txt .
RUN --mount=type=cache,target=/root/.cache/pip pip install -r requirements.txt

COPY . .
# Dossier de travail (fichiers temporaires, contenus indexés, cache des vidéos) : volume en production
RUN mkdir -p /app/uploads && chown -R lms:lms /app/uploads
USER lms

EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/api/live', timeout=4)" || exit 1
CMD ["sh", "-c", "exec uvicorn main:app --host 0.0.0.0 --port 8000 --workers ${UVICORN_WORKERS} --proxy-headers"]
