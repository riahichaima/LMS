"""Indexation de contenus pour l'assistant IA — appelée par Spring Boot (LmsAiService)."""

import shutil
import tempfile
from pathlib import Path

from fastapi import APIRouter, File, HTTPException, UploadFile

from api.schemas import URLIngestRequest
from config import TMP_PATH
from services.content_extractor import extract_from_pdf, extract_from_url, is_youtube_url
from services.content_store import save_content
from services.video_processor import analyze_video, analyze_youtube

router = APIRouter()

VIDEO_EXTENSIONS = (".mp4", ".avi", ".mkv", ".webm", ".mov", ".wav", ".mp3", ".m4a")
TEXT_EXTENSIONS = (".txt", ".md")


def _preview(text: str, size: int = 300) -> str:
    return text[:size] + "..." if len(text) > size else text


def _save_upload(file: UploadFile) -> Path:
    path = Path(tempfile.mkdtemp(dir=TMP_PATH)) / Path(file.filename or "fichier").name
    with path.open("wb") as f:
        shutil.copyfileobj(file.file, f)
    return path


@router.post("/upload/pdf")
def upload_document(file: UploadFile = File(...)):
    """Indexer un document (PDF, TXT, MD)."""
    name = (file.filename or "").lower()
    if not name.endswith((".pdf",) + TEXT_EXTENSIONS):
        raise HTTPException(status_code=400, detail="Formats acceptés : PDF, TXT, MD.")

    path = _save_upload(file)
    try:
        text = path.read_text(encoding="utf-8", errors="replace") if name.endswith(TEXT_EXTENSIONS) else extract_from_pdf(str(path))
    except Exception as e:
        raise HTTPException(status_code=422, detail=f"Erreur d'extraction : {e}")
    finally:
        shutil.rmtree(path.parent, ignore_errors=True)

    content = save_content(file.filename, "pdf" if name.endswith(".pdf") else "text", text)
    return {
        "id": content["id"],
        "title": content["title"],
        "contentType": content["content_type"],
        "message": f"Document indexé ({len(text)} caractères extraits).",
        "textPreview": _preview(text),
    }


@router.post("/upload/video")
def upload_video(file: UploadFile = File(...)):
    """Indexer une vidéo locale : transcription + analyse des images."""
    if not (file.filename or "").lower().endswith(VIDEO_EXTENSIONS):
        raise HTTPException(status_code=400, detail=f"Formats acceptés : {', '.join(VIDEO_EXTENSIONS)}")

    path = _save_upload(file)
    try:
        text = analyze_video(str(path))
    except Exception as e:
        raise HTTPException(status_code=422, detail=f"Erreur d'analyse vidéo : {e}")
    finally:
        shutil.rmtree(path.parent, ignore_errors=True)

    content = save_content(file.filename, "video", text)
    return {
        "id": content["id"],
        "title": content["title"],
        "contentType": "video",
        "transcriptPreview": _preview(text),
        "segmentsCount": text.count("\n") + 1,
        "message": "Vidéo analysée et indexée.",
    }


@router.post("/ingest/url")
def ingest_url(request: URLIngestRequest):
    """Indexer un lien : vidéo YouTube, page web, PDF en ligne, Google Docs/Drive."""
    youtube = is_youtube_url(request.url)
    try:
        text = analyze_youtube(request.url) if youtube else extract_from_url(request.url)
    except Exception as e:
        raise HTTPException(status_code=422, detail=f"Erreur d'ingestion : {e}")

    content = save_content(request.title or request.url[:100], "youtube" if youtube else "web", text, request.url)
    return {
        "id": content["id"],
        "title": content["title"],
        "contentType": content["content_type"],
        "message": f"Contenu indexé ({len(text)} caractères).",
        "textPreview": _preview(text),
    }
