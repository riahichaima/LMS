"""Transforme les ressources envoyées par le backend (PDF, vidéo, lien...) en texte de cours."""

import hashlib
import logging
from pathlib import Path

from api.schemas import ResourceDetail
from config import EXTRACT_CACHE_PATH, settings
from services import pdf_ocr
from services.content_extractor import extract_from_pdf, extract_from_url, is_youtube_url
from services.video_processor import analyze_audio, analyze_video, analyze_youtube

AUDIO_EXTENSIONS = (".mp3", ".wav", ".m4a", ".ogg", ".aac", ".flac")
# On compte les lettres (toutes langues : arabe, latin...) et non les caractères : un PDF scanné, ou un PDF
# arabe dont la police n'a pas de correspondance Unicode, ne donne que des numéros de page et des symboles.
MIN_LETTERS_PER_PAGE = 30  # en dessous : les pages sont lues comme des images par le modèle
MIN_LETTERS_PER_RESOURCE = 20  # en dessous : ressource considérée comme illisible (jamais de quiz inventé)


def count_letters(text: str) -> int:
    """Nombre de lettres, quelle que soit l'écriture (les chiffres et symboles ne comptent pas)."""
    return sum(1 for c in text if c.isalpha())

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
    uploads = (root / "uploads").resolve()
    candidates = [path] if path.is_absolute() else [root / path, path]
    tail = _uploads_tail(file_path)
    if tail:
        candidates.append(uploads.joinpath(*tail))
    for candidate in candidates:
        resolved = candidate.resolve()
        # Jamais un fichier hors du dossier uploads du backend (ex. chemin forgé dans une ressource)
        if resolved.is_relative_to(uploads) and resolved.is_file():
            return resolved
    raise FileNotFoundError(f"Fichier introuvable : {file_path} (BACKEND_ROOT_DIR={settings.BACKEND_ROOT_DIR})")


def _cached(key: str, compute) -> str:
    """Analyser une vidéo coûte cher (crédits Azure) : le texte est gardé sur disque.

    compute=None : lecture du cache seulement (index du RAG : aucune nouvelle analyse payante).
    """
    path = EXTRACT_CACHE_PATH / f"{hashlib.sha1(key.encode('utf-8')).hexdigest()}.txt"
    if path.is_file():
        logger.info("Contenu repris du cache (%s)", key)
        return path.read_text(encoding="utf-8")
    if compute is None:
        raise ValueError("pas encore analysé (générez d'abord un quiz sur cette ressource)")
    text = compute()
    if text.strip():
        path.write_text(text, encoding="utf-8")
    return text


def _pdf_text(pdf_path: Path, cache_only: bool = False) -> str:
    """Texte d'un PDF ; PDF scanné (images sans texte) : pages lues par le modèle, résultat gardé en cache."""
    try:
        text = extract_from_pdf(str(pdf_path))
    except ValueError:
        text = ""
    try:
        pages = max(pdf_ocr.page_count(str(pdf_path)), 1)
    except Exception:
        pages = 1  # PDF que PDFium ne sait pas ouvrir : le texte extrait est gardé tel quel
    if count_letters(text) >= MIN_LETTERS_PER_PAGE * pages:
        return text
    stat = pdf_path.stat()
    logger.info("PDF sans texte exploitable (%d lettres, %d pages) : lecture des images", count_letters(text), pages)
    try:
        return _cached(f"pdf-ocr:{pdf_path.resolve()}:{stat.st_size}:{stat.st_mtime_ns}",
                       None if cache_only else (lambda: pdf_ocr.ocr_pdf(str(pdf_path))))
    except Exception as e:
        if text.strip():
            logger.warning("Lecture des images du PDF impossible (%s) : texte extrait conservé", e)
            return text
        raise


def extract_resource(resource: ResourceDetail, media_from_cache_only: bool = False) -> str:
    """Extraire le texte d'une ressource selon son type.

    media_from_cache_only : vidéos, audios, YouTube et PDF scannés ne sont pas réanalysés (texte du cache
    seulement) ; utilisé par l'index du RAG pour ne jamais lancer d'analyse longue et payante.
    """
    rtype = resource.type.upper()
    url = resource.external_url

    if rtype == "TEXT":
        return resource.title
    # Seul un vrai lien YouTube part vers yt-dlp ; tout autre lien passe par le téléchargement protégé (SSRF)
    if url and is_youtube_url(url):
        return _cached(f"youtube:{url}", None if media_from_cache_only else (lambda: analyze_youtube(url)))
    if resource.file_path and rtype == "PDF":
        return _pdf_text(resolve_backend_path(resource.file_path), cache_only=media_from_cache_only)
    if resource.file_path and rtype in ("VIDEO", "AUDIO"):
        media = resolve_backend_path(resource.file_path)
        stat = media.stat()
        is_audio = rtype == "AUDIO" or media.suffix.lower() in AUDIO_EXTENSIONS
        analyze = analyze_audio if is_audio else analyze_video
        # La clé change si le fichier est remplacé (taille / date)
        return _cached(f"media:{media.resolve()}:{stat.st_size}:{stat.st_mtime_ns}",
                       None if media_from_cache_only else (lambda: analyze(str(media))))
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
        if count_letters(text) >= MIN_LETTERS_PER_RESOURCE:
            parts.append(f"--- {resource.title} ({resource.type}) ---\n{text[:per_resource]}")
        else:
            # Que des numéros de page ou des symboles : on ne génère rien à partir de ça
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
