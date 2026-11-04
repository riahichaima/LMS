"""Questions des étudiants à l'assistant IA et chatbot du cours — appelées par Spring Boot."""

import hashlib
import json
import threading
from collections import OrderedDict

from fastapi import APIRouter, HTTPException

from agents.tutor_agent import TutorAgent
from api.schemas import CourseChatRequest, QuestionRequest
from services.content_store import get_content, get_latest_content
from services.resource_loader import build_course_content

router = APIRouter()
tutor_agent = TutorAgent()

# Contenu des cours déjà extrait (PDF, pages web...) : évite de le relire à chaque message du chatbot
_COURSE_CONTENT_CACHE: "OrderedDict[str, str]" = OrderedDict()
_COURSE_CONTENT_CACHE_SIZE = 32
_cache_lock = threading.Lock()


def _course_content(request: CourseChatRequest) -> str:
    key = hashlib.sha1(json.dumps(
        [request.lesson_descriptions, [r.model_dump() for r in request.resource_details]],
        sort_keys=True, ensure_ascii=False,
    ).encode("utf-8")).hexdigest()
    with _cache_lock:
        if key in _COURSE_CONTENT_CACHE:
            _COURSE_CONTENT_CACHE.move_to_end(key)
            return _COURSE_CONTENT_CACHE[key]
    content = build_course_content(request.lesson_descriptions, request.resource_details)
    with _cache_lock:
        _COURSE_CONTENT_CACHE[key] = content
        while len(_COURSE_CONTENT_CACHE) > _COURSE_CONTENT_CACHE_SIZE:
            _COURSE_CONTENT_CACHE.popitem(last=False)
    return content


@router.post("/course")
def course_chat(request: CourseChatRequest):
    """Chatbot du cours : explique, répond aux questions et résume le cours ou la leçon ouverte."""
    content = _course_content(request)
    if not content.strip():
        content = "(Le contenu détaillé du cours n'est pas disponible : appuie-toi sur le titre du cours.)"
    try:
        answer = tutor_agent.chat_about_course(
            content=content,
            course_title=request.course_title,
            messages=[m.model_dump() for m in request.messages],
            lesson_title=request.lesson_title,
            summary=request.action == "summary",
        )
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Erreur du chatbot : {e}") from e
    return {"answer": answer}


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
        raise HTTPException(status_code=500, detail=f"Erreur de l'agent : {e}") from e

    return {
        "answer": answer,
        "agent": tutor_agent.name,
        "sources": [content["title"]] if content.get("title") else None,
    }
