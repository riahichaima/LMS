"""PDF scannés : pages lues par le modèle (simulé ici), une seule fois grâce au cache."""

import pytest
from PIL import Image, ImageDraw

from config import settings
from services import pdf_ocr, resource_loader


class FakeVision:
    def __init__(self):
        self.calls = []

    def describe_images(self, prompt, images, detail="low", max_tokens=None):
        self.calls.append((prompt, len(images), detail))
        assert max_tokens and max_tokens <= 4000
        return "Page : Le modèle OSI comporte sept couches."


@pytest.fixture
def scanned_pdf(tmp_path):
    """PDF de 3 pages ne contenant que des images (comme un document scanné)."""
    pages = []
    for i in range(3):
        img = Image.new("RGB", (1240, 1754), "white")
        ImageDraw.Draw(img).text((100, 100), f"Page {i + 1} : couches OSI", fill="black")
        pages.append(img)
    path = tmp_path / "scan.pdf"
    pages[0].save(path, format="PDF", save_all=True, append_images=pages[1:])
    return path


@pytest.fixture
def vision(monkeypatch, tmp_path):
    fake = FakeVision()
    monkeypatch.setattr(pdf_ocr, "get_llm_client", lambda: fake)
    monkeypatch.setattr(resource_loader, "EXTRACT_CACHE_PATH", tmp_path)
    return fake


def test_scanned_pdf_is_read_by_the_model(scanned_pdf, vision):
    text = resource_loader._pdf_text(scanned_pdf)
    assert "OSI" in text and "[Pages 1-2]" in text and "[Pages 3-3]" in text
    # 2 pages par requête, en détail « high » pour lire le texte
    assert sorted(n for _, n, _ in vision.calls) == [1, 2]
    assert all(detail == "high" for _, _, detail in vision.calls)


def test_scanned_pdf_is_read_only_once(scanned_pdf, vision):
    resource_loader._pdf_text(scanned_pdf)
    first = len(vision.calls)
    resource_loader._pdf_text(scanned_pdf)
    assert len(vision.calls) == first


def test_pdf_with_text_does_not_call_the_model(scanned_pdf, vision, monkeypatch):
    monkeypatch.setattr(resource_loader, "extract_from_pdf", lambda path: "Le protocole TCP. " * 50)
    assert "TCP" in resource_loader._pdf_text(scanned_pdf)
    assert vision.calls == []


def test_page_limit(scanned_pdf, vision, monkeypatch):
    monkeypatch.setattr(settings, "PDF_OCR_MAX_PAGES", 2)
    text = pdf_ocr.ocr_pdf(str(scanned_pdf))
    assert "1 page(s) suivante(s) non lues" in text
    assert sum(n for _, n, _ in vision.calls) == 2


def test_short_text_kept_when_images_cannot_be_read(scanned_pdf, monkeypatch, tmp_path):
    class Down:
        def describe_images(self, *args, **kwargs):
            raise RuntimeError("Azure indisponible")
    monkeypatch.setattr(pdf_ocr, "get_llm_client", lambda: Down())
    monkeypatch.setattr(resource_loader, "EXTRACT_CACHE_PATH", tmp_path)
    monkeypatch.setattr(resource_loader, "extract_from_pdf", lambda path: "Résumé : TCP.")
    assert resource_loader._pdf_text(scanned_pdf) == "Résumé : TCP."


def test_failed_batch_is_retried_once(scanned_pdf, monkeypatch):
    class Flaky(FakeVision):
        failed = False

        def describe_images(self, prompt, images, detail="low", max_tokens=None):
            if not Flaky.failed:
                Flaky.failed = True
                raise TimeoutError("La requête Azure OpenAI a expiré.")
            return super().describe_images(prompt, images, detail, max_tokens)
    monkeypatch.setattr(pdf_ocr, "get_llm_client", lambda: Flaky())
    assert "OSI" in pdf_ocr.ocr_pdf(str(scanned_pdf))
