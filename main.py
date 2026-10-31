"""Point d'entrée — service IA du LMS (appelé par le backend Spring Boot)."""

import logging

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from agents.llm_client import describe_provider, get_llm_client
from api import assessment_routes, chat_routes, content_routes, correction_routes, progress_routes

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")

app = FastAPI(
    title="LMS AI",
    description=(
        "Service IA (Azure AI Foundry, gpt-4.1-mini) : analyse des PDF, vidéos et liens d'un cours, "
        "génération de quiz/TP par niveau avec correction, correction des réponses, assistant pédagogique."
    ),
    version="3.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(assessment_routes.router, prefix="/api/orchestrator", tags=["Génération"])
app.include_router(correction_routes.router, prefix="/api/correct", tags=["Correction"])
app.include_router(progress_routes.router, prefix="/api/progress", tags=["Progression"])
app.include_router(content_routes.router, prefix="/api/content", tags=["Assistant — contenus"])
app.include_router(chat_routes.router, prefix="/api/chat", tags=["Assistant — questions"])


@app.get("/api/health", tags=["System"])
def health_check():
    info = describe_provider()
    try:
        available = get_llm_client().is_available()
        error = None
    except Exception as e:  # ex: clé Azure manquante
        available, error = False, str(e)
    return {"status": "ok", "model_available": available, "error": error, **info}
