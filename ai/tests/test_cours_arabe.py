"""Cours en arabe : un PDF dont seuls les numéros de page sont lisibles est lu comme des images,
jamais transformé en questions sur « le document » ; les questions suivent la langue du cours."""

import json

import pytest
from PIL import Image

from agents.quiz_agent import QuizAgent
from api.schemas import ResourceDetail
from prompts.quiz_prompts import QCM_GENERATION_PROMPT, TP_GENERATION_PROMPT
from services import assessment_service, course_profile, pdf_ocr, resource_loader

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
    assert "{course_rules}" in prompt and "sans mélanger les langues" in prompt
    assert "Ne pose jamais de question sur le document lui-même" in prompt


# ---------------------------------------------------------------- Langue et type d'exercice imposés par le code
ARABIC_COURSE = ("--- درس 1 (PDF) ---\n[Pages 1-2]\n"
                 "الأعداد العشرية : لجمع عددين عشريين نضع الفواصل تحت بعضها ثم نجمع كما في الأعداد الطبيعية. "
                 "مثال : 14,927 + 6,125 = 21,052. لضرب عدد عشري في 10 ننقل الفاصلة رتبة واحدة إلى اليمين.")
PYTHON_COURSE = ("Les fonctions en Python.\n```python\ndef somme(a, b):\n    return a + b\nprint(somme(1, 2))\n```\n"
                 "import math permet d'utiliser math.sqrt.")


def test_course_profile():
    assert course_profile.course_language(ARABIC_COURSE) == "ar"
    assert course_profile.course_language("Les nombres décimaux et la virgule.") == "fr"
    assert not course_profile.is_programming_course(ARABIC_COURSE)
    assert course_profile.is_programming_course(PYTHON_COURSE)
    assert course_profile.is_in_language("ما هو ناتج 3,7 × 10 ؟", "ar")
    assert not course_profile.is_in_language("Que devient la virgule quand on multiplie par 100 ?", "ar")


def _qcm(question, choices, explanation):
    return {"question": question, "choices": choices, "answer": "B", "explanation": explanation}


def test_arabic_course_rejects_french_questions_and_asks_again(fake_llm):
    french = _qcm("Que devient la virgule ?", ["À gauche", "À droite", "Fixe", "Disparaît"], "Elle va à droite.")
    arabic = _qcm("عند ضرب عدد عشري في 10، أين تنتقل الفاصلة ؟", ["إلى اليسار", "إلى اليمين", "لا تتحرك", "تختفي"],
                  "تنتقل الفاصلة رتبة واحدة إلى اليمين.")
    llm = fake_llm([json.dumps({"questions": [french]}), json.dumps({"questions": [arabic]})])

    questions = QuizAgent().generate_qcm(ARABIC_COURSE, "advanced", count=1)

    assert [q["question"] for q in questions] == [arabic["question"]]
    assert len(llm.prompts) == 2
    assert "LANGUE : arabe, obligatoire" in llm.prompts[0]


def test_no_code_exercise_for_a_course_that_does_not_teach_programming(fake_llm):
    code = {"title": "خوارزمية", "statement": "اكتب دالة بايثون تحسب مجموع الأعداد من 1 إلى n.",
            "language": "python", "solution_code": "def s(n):\n    return n * (n + 1) // 2",
            "solution_explanation": "نستعمل القاعدة.", "type": "code"}
    text = {"title": "جمع عددين عشريين", "statement": "احسب 14,927 + 6,125 مع وضع الفواصل تحت بعضها.",
            "solution_explanation": "النتيجة 21,052 لأننا نجمع الأجزاء العشرية ثم الصحيحة.", "type": "text"}
    llm = fake_llm([json.dumps({"exercises": [code]}), json.dumps({"exercises": [text]})])

    exercises = QuizAgent().generate_tp(ARABIC_COURSE, "advanced", count=1)

    assert [ex["title"] for ex in exercises] == ["جمع عددين عشريين"]
    assert "AUCUN exercice de code" in llm.prompts[0]


def test_programming_course_keeps_code_exercises(fake_llm):
    code = {"title": "Somme", "statement": "Écrire une fonction somme(a, b).", "language": "python",
            "solution_code": "def somme(a, b):\n    return a + b", "solution_explanation": "On renvoie a + b.",
            "type": "code"}
    llm = fake_llm([json.dumps({"exercises": [code]})])
    exercises = QuizAgent().generate_tp(PYTHON_COURSE, "intermediate", count=1)
    assert exercises[0]["type"] == "code"
    assert "cours de programmation" in llm.prompts[0]
