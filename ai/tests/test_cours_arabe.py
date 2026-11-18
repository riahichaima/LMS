"""Cours en arabe : un PDF dont seuls les numéros de page sont lisibles est lu comme des images,
jamais transformé en questions sur « le document » ; les questions suivent la langue du cours."""

import pytest
from PIL import Image

from api.schemas import ResourceDetail
from prompts.quiz_prompts import QCM_GENERATION_PROMPT, TP_GENERATION_PROMPT
from services import assessment_service, pdf_ocr, resource_loader

PAGE_NUMBERS_ONLY = "\n\n".join(str(i) for i in range(1, 41))  # ce que donne un PDF arabe sans texte Unicode


class ArabicVision:
    def __init__(self):
        self.calls = 0

    def describe_images(self, prompt, images, detail="low", max_tokens=None):
        self.calls += 1
        return "الدرس الأول : المعادلات من الدرجة الثانية وحلها باستعمال المميز دلتا."


@pytest.fixture
def pdf(tmp_path):
    path = tmp_path / "cours-arabe.pdf"
    Image.new("RGB", (620, 877), "white").save(path, format="PDF")
    return path


def test_letters_are_counted_in_every_script():
    assert resource_loader.count_letters("مرحبا") == 5
    assert resource_loader.count_letters("Cours 12") == 5
    assert resource_loader.count_letters(PAGE_NUMBERS_ONLY) == 0


def test_pdf_with_only_page_numbers_is_read_by_the_model(pdf, monkeypatch, tmp_path):
    vision = ArabicVision()
    monkeypatch.setattr(pdf_ocr, "get_llm_client", lambda: vision)
    monkeypatch.setattr(resource_loader, "EXTRACT_CACHE_PATH", tmp_path)
    monkeypatch.setattr(resource_loader, "extract_from_pdf", lambda path: PAGE_NUMBERS_ONLY)
    text = resource_loader._pdf_text(pdf)
    assert vision.calls == 1
    assert "المعادلات" in text


def test_page_numbers_alone_never_become_a_quiz(monkeypatch):
    monkeypatch.setattr(resource_loader, "extract_resource", lambda resource: PAGE_NUMBERS_ONLY)
    resource = ResourceDetail(title="درس 1", type="PDF", file_path="/app/uploads/cours.pdf")
    text, unreadable = resource_loader.extract_resources_text([resource])
    assert text == "" and unreadable == ["درس 1"]
    with pytest.raises(ValueError, match="n'a pas pu être lu"):
        assessment_service.build_assessment_content([], [resource])


@pytest.mark.parametrize("prompt", [QCM_GENERATION_PROMPT, TP_GENERATION_PROMPT])
def test_prompts_follow_the_course_language_and_ignore_the_file(prompt):
    assert "Rédige en français" not in prompt
    assert "en arabe si le cours est en arabe" in prompt
    assert "Ne pose jamais de question sur le document lui-même" in prompt
