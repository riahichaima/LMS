"""Transforme les ressources envoyées par le backend (PDF, vidéo, lien...) en texte de cours."""

import hashlib
import logging
from pathlib import Path

from api.schemas import ResourceDetail
from config import EXTRACT_CACHE_PATH, settings
from services.content_extractor import extract_from_pdf, extract_from_url, is_youtube_url
from services.video_processor import analyze_audio, analyze_video, analyze_youtube

AUDIO_EXTENSIONS = (".mp3", ".wav", ".m4a", ".ogg", ".aac", ".flac")

logger = logging.getLogger(__name__)


def _uploads_tail(file_path: str) -> list[str] | None:
    """Partie du chemin après le dernier dossier « uploads » (séparateurs Windows ou Linux)."""
    parts = [p for p in file_path.replace("\\", "/").split("/") if p]
    if "uploads" not in parts:
        return None
    tail = parts[len(parts) - 1 - parts[::-1].index("uploads") + 1:]
    return tail if tail and ".." not in tail else None


def resolve_backend_path(file_path: str) -> Path:
    """Trouver un fichier uploadé par le backend.

    Le backend enregistre le chemin vu depuis sa machine ou son conteneur (/app/uploads/videos/x.pdf,
    C:\\...\\backend\\uploads\\videos\\x.pdf) ; le service IA voit le même dossier sous
    BACKEND_ROOT_DIR/uploads (Docker : /backend/uploads, en lecture seule). Le chemin est donc retraduit,
    sans jamais sortir du dossier uploads.
    """
    path = Path(file_path)
    root = Path(settings.BACKEND_ROOT_DIR)
    candidates = [path] if path.is_absolute() else [root / path, path]
    tail = _uploads_tail(file_path)
    if tail:
        uploads = (root / "uploads").resolve()
        mapped = uploads.joinpath(*tail).resolve()
        if mapped.is_relative_to(uploads):
            candidates.append(mapped)
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    raise FileNotFoundError(f"Fichier introuvable : {file_path} (BACKEND_ROOT_DIR={settings.BACKEND_ROOT_DIR})")


def _cached(key: str, compute) -> str:
    """Analyser une vidéo coûte cher (crédits Azure) : le texte est gardé sur disque."""
    path = EXTRACT_CACHE_PATH / f"{hashlib.sha1(key.encode('utf-8')).hexdigest()}.txt"
    if path.is_file():
        logger.info("Contenu vidéo repris du cache (%s)", key)
        return path.read_text(encoding="utf-8")
    text = compute()
    if text.strip():
        path.write_text(text, encoding="utf-8")
    return text


def extract_resource(resource: ResourceDetail) -> str:
    """Extraire le texte d'une ressource selon son type."""
    rtype = resource.type.upper()
    url = resource.external_url

    if rtype == "TEXT":
        return resource.title
    # Seul un vrai lien YouTube part vers yt-dlp ; tout autre lien passe par le téléchargement protégé (SSRF)
    if url and is_youtube_url(url):
        return _cached(f"youtube:{url}", lambda: analyze_youtube(url))
    if resource.file_path and rtype == "PDF":
        return extract_from_pdf(str(resolve_backend_path(resource.file_path)))
    if resource.file_path and rtype in ("VIDEO", "AUDIO"):
        media = resolve_backend_path(resource.file_path)
        stat = media.stat()
        is_audio = rtype == "AUDIO" or media.suffix.lower() in AUDIO_EXTENSIONS
        analyze = analyze_audio if is_audio else analyze_video
        # La clé change si le fichier est remplacé (taille / date)
        return _cached(f"media:{media.resolve()}:{stat.st_size}:{stat.st_mtime_ns}", lambda: analyze(str(media)))
    if url:
        return extract_from_url(url)
    raise ValueError(f"Ressource '{resource.title}' ({rtype}) sans fichier ni lien.")


def extract_resources_text(resources: list[ResourceDetail]) -> tuple[str, list[str]]:
    """Texte des ressources (PDF, vidéos, liens…) et titres de celles qui n'ont pas pu être lues."""
    parts, unreadable = [], []
    per_resource = max(1000, settings.max_content_chars // max(len(resources), 1))
    for resource in resources:
        try:
            text = extract_resource(resource).strip()
        except Exception as e:
            logger.warning("Ressource ignorée '%s' (%s) : %s", resource.title, resource.type, e)
            unreadable.append(resource.title or resource.type)
            continue
        if text:
            parts.append(f"--- {resource.title} ({resource.type}) ---\n{text[:per_resource]}")
        else:
            unreadable.append(resource.title or resource.type)
    return "\n\n".join(parts), unreadable


def build_course_content(lesson_descriptions: list[str | None], resources: list[ResourceDetail]) -> str:
    """Assembler tout le contenu du cours. Une ressource illisible est ignorée (et loggée).

    Le budget MAX_CONTENT_CHARS est partagé entre les ressources pour qu'un gros PDF
    ne cache pas les autres.
    """
    parts = [d for d in lesson_descriptions if d]
    per_resource = max(1000, settings.max_content_chars // max(len(resources), 1))

    for resource in resources:
        try:
            text = extract_resource(resource).strip()
        except Exception as e:
            logger.warning("Ressource ignorée '%s' (%s) : %s", resource.title, resource.type, e)
            continue
        if text:
            parts.append(f"--- {resource.title} ({resource.type}) ---\n{text[:per_resource]}")

    return "\n\n".join(parts)
