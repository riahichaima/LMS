"""Questions des étudiants à l'assistant IA — appelée par Spring Boot (LmsAiService)."""

from fastapi import APIRouter, HTTPException

from agents.tutor_agent import TutorAgent
from api.schemas import QuestionRequest
from services.content_store import get_content, get_latest_content

router = APIRouter()
tutor_agent = TutorAgent()


@router.post("/ask")
def ask_question(request: QuestionRequest):
    """Répondre à partir d'un contenu : content_id, texte brut, ou à défaut le dernier contenu indexé."""
    if request.raw_content:
        content = {"text": request.raw_content, "content_type": "texte", "title": None}
    elif request.content_id:
        content = get_content(request.content_id)
        if not content:
            raise HTTPException(status_code=404, detail="Contenu introuvable.")
    else:
        content = get_latest_content()
        if not content:
            raise HTTPException(status_code=400, detail="Aucun contenu indexé : téléversez d'abord un document ou une vidéo.")

    try:
        answer = tutor_agent.answer(content["text"], request.question, request.level, content["content_type"])
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Erreur de l'agent : {e}")

    return {
        "answer": answer,
        "agent": tutor_agent.name,
        "sources": [content["title"]] if content.get("title") else None,
    }
