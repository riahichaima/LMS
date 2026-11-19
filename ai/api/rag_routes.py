"""RAG de l'administration : index de tous les cours et questions avec sources (appelé par Spring Boot)."""

from fastapi import APIRouter, HTTPException

from api.schemas import RagAskRequest, RagIndexRequest
from services import rag

router = APIRouter()


@router.post("/index")
def build_index(request: RagIndexRequest):
    """Reconstruire l'index à partir des leçons de tous les cours."""
    return rag.build_index(request.lessons, request.analyze_media)


@router.get("/status")
def index_status():
    return rag.status()


@router.post("/ask")
def ask(request: RagAskRequest):
    """Répondre à partir des passages les plus proches de la question, avec leurs sources."""
    try:
        return rag.ask(request.question, request.top_k)
    except LookupError as e:
        raise HTTPException(status_code=409, detail=str(e)) from e
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e
