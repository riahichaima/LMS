"""Extraction de texte depuis un PDF, une page web ou un lien Google Drive/Docs."""

import io
import re

import httpx
from bs4 import BeautifulSoup
from pypdf import PdfReader

_HEADERS = {"User-Agent": "Mozilla/5.0 (compatible; LMS_AI/1.0)"}


def is_youtube_url(url: str) -> bool:
    return "youtube.com" in url or "youtu.be" in url


def extract_from_pdf(source) -> str:
    """Extraire le texte d'un PDF (chemin de fichier ou flux binaire)."""
    reader = PdfReader(source)
    text = "\n\n".join(t for t in (page.extract_text() for page in reader.pages) if t)
    if not text.strip():
        raise ValueError("Le PDF ne contient pas de texte extractible (PDF scanné ?).")
    return text


def extract_from_url(url: str) -> str:
    """Extraire le texte d'un lien externe (page web, PDF en ligne, Google Docs/Drive)."""
    url = _google_export_url(url)
    with httpx.Client(timeout=30.0, follow_redirects=True, headers=_HEADERS) as client:
        response = client.get(url)
        response.raise_for_status()

    content_type = response.headers.get("content-type", "")
    if "pdf" in content_type or url.lower().endswith(".pdf"):
        return extract_from_pdf(io.BytesIO(response.content))
    if "html" not in content_type:
        return response.text

    soup = BeautifulSoup(response.text, "lxml")
    for element in soup(["script", "style", "nav", "footer", "header", "aside"]):
        element.decompose()
    lines = (line.strip() for line in soup.get_text(separator="\n").splitlines())
    return "\n".join(line for line in lines if line)


def _google_export_url(url: str) -> str:
    """Convertir un lien Google Docs/Drive en lien de téléchargement direct."""
    match = re.search(r"/d/([a-zA-Z0-9_-]+)", url) or re.search(r"id=([a-zA-Z0-9_-]+)", url)
    if not match:
        return url
    if "docs.google.com/document" in url:
        return f"https://docs.google.com/document/d/{match.group(1)}/export?format=txt"
    if "drive.google.com" in url:
        return f"https://drive.google.com/uc?export=download&id={match.group(1)}"
    return url
