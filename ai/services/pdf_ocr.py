"""Lecture des PDF scannés (pages enregistrées comme des images, sans texte extractible).

Chaque page est rendue en image puis recopiée par le modèle de chat (vision), quelques pages par
requête. Le résultat est mis en cache par resource_loader : un PDF n'est lu qu'une seule fois.
"""

import base64
import io
import logging
from concurrent.futures import ThreadPoolExecutor

import pypdfium2 as pdfium

from agents.llm_client import get_llm_client
from config import settings
from prompts.pdf_prompts import PDF_OCR_PROMPT

logger = logging.getLogger(__name__)

PAGE_WIDTH_PX = 1400      # largeur de rendu : texte lisible par le modèle en détail « high »
PAGES_PER_REQUEST = 2     # peu de pages par requête : réponse complète, débit Azure respecté
PARALLEL_REQUESTS = 3


def _page_jpeg_b64(pdf: pdfium.PdfDocument, index: int) -> str:
    page = pdf[index]
    try:
        bitmap = page.render(scale=PAGE_WIDTH_PX / max(page.get_width(), 1))
        out = io.BytesIO()
        bitmap.to_pil().convert("RGB").save(out, format="JPEG", quality=70)
        return base64.b64encode(out.getvalue()).decode()
    finally:
        page.close()


def page_count(path: str) -> int:
    pdf = pdfium.PdfDocument(path)
    try:
        return len(pdf)
    finally:
        pdf.close()


def ocr_pdf(path: str) -> str:
    """Texte d'un PDF scanné, page par page (au plus PDF_OCR_MAX_PAGES pages)."""
    pdf = pdfium.PdfDocument(path)
    try:
        total = len(pdf)
        count = min(total, settings.PDF_OCR_MAX_PAGES)
        if count == 0:
            return ""
        batches = []
        for start in range(0, count, PAGES_PER_REQUEST):
            pages = range(start, min(start + PAGES_PER_REQUEST, count))
            batches.append((start + 1, pages[-1] + 1, [_page_jpeg_b64(pdf, i) for i in pages]))
    finally:
        pdf.close()

    client = get_llm_client()
    logger.info("PDF scanné : lecture de %d page(s) sur %d par le modèle", count, total)

    def read(batch):
        first, last, images = batch
        text = client.describe_images(PDF_OCR_PROMPT.format(first=first, last=last), images, detail="high")
        return f"[Pages {first}-{last}]\n{text.strip()}"

    with ThreadPoolExecutor(max_workers=PARALLEL_REQUESTS) as executor:
        texts = list(executor.map(read, batches))
    if total > count:
        texts.append(f"[{total - count} page(s) suivante(s) non lues : limite PDF_OCR_MAX_PAGES={count}]")
    return "\n\n".join(texts)
