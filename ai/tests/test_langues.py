"""Toutes les langues : la langue du cours est détectée par le code, imposée au modèle puis vérifiée."""

import json

import pytest

from agents.quiz_agent import QuizAgent
from services.course_profile import course_language, is_in_language, language_name

COURSES = {
    "fr": "Le théorème de Pythagore est vrai dans un triangle rectangle : le carré de l'hypoténuse est égal à la somme des carrés.",
    "en": "The Pythagorean theorem is true in a right triangle: the square of the hypotenuse is the sum of the squares of the other sides.",
    "es": "El teorema de Pitágoras es válido en un triángulo rectángulo: el cuadrado de la hipotenusa es igual a la suma de los cuadrados.",
    "de": "Der Satz des Pythagoras gilt im rechtwinkligen Dreieck: das Quadrat der Hypotenuse ist gleich der Summe der Quadrate.",
    "it": "Il teorema di Pitagora è valido in un triangolo rettangolo: il quadrato dell'ipotenusa è uguale alla somma dei quadrati.",
    "pt": "O teorema de Pitágoras é válido em um triângulo retângulo: o quadrado da hipotenusa é igual à soma dos quadrados dos catetos, e não é difícil.",
    "ar": "نظرية فيثاغورس صحيحة في المثلث القائم : مربع الوتر يساوي مجموع مربعي الضلعين الآخرين.",
    "ru": "Теорема Пифагора верна в прямоугольном треугольнике: квадрат гипотенузы равен сумме квадратов катетов.",
    "el": "Το Πυθαγόρειο θεώρημα ισχύει σε ορθογώνιο τρίγωνο: το τετράγωνο της υποτείνουσας ισούται με το άθροισμα.",
    "he": "משפט פיתגורס נכון במשולש ישר זווית: ריבוע היתר שווה לסכום ריבועי הניצבים.",
    "zh": "勾股定理在直角三角形中成立：斜边的平方等于两条直角边的平方和。",
    "ja": "ピタゴラスの定理は直角三角形で成り立ちます。斜辺の二乗は他の二辺の二乗の和に等しいです。",
    "ko": "피타고라스 정리는 직각삼각형에서 성립합니다. 빗변의 제곱은 다른 두 변의 제곱의 합과 같습니다.",
    "hi": "पाइथागोरस प्रमेय समकोण त्रिभुज में सत्य है: कर्ण का वर्ग अन्य दो भुजाओं के वर्गों के योग के बराबर है।",
}


@pytest.mark.parametrize("language", sorted(COURSES))
def test_course_language_is_detected(language):
    # Repères ajoutés par la plateforme, toujours en caractères latins
    content = f"--- Leçon 1 (PDF) ---\n[Pages 1-2]\n{COURSES[language]}"
    assert course_language(content) == language


def test_unknown_language_falls_back_to_french():
    assert course_language("12 + 7 = 19 ; 3 × 4 = 12") == "fr"
    assert language_name("xx") == "français"


def test_generated_text_must_match_the_course_language():
    assert is_in_language("¿Cuál es el cuadrado de la hipotenusa en un triángulo rectángulo?", "es")
    assert not is_in_language("What is the square of the hypotenuse and how is it computed?", "es")
    assert not is_in_language("Quel est le carré de l'hypoténuse dans un triangle et pour quoi faire ?", "en")
    assert not is_in_language("ما هو مربع الوتر ؟", "en")
    assert is_in_language("Чему равен квадрат гипотенузы?", "ru")
    assert is_in_language("3 cm × 4 cm", "de")  # trop court pour juger : accepté


def test_code_keywords_do_not_change_the_language():
    question = ("Écrire une fonction qui renvoie la somme des carrés et qui vérifie le résultat.\n\n"
                "```python\nfor x in values:\n    if x is None and y is not None:\n        return the_sum\n```")
    assert is_in_language(question, "fr")


def test_spanish_course_rejects_english_questions_and_asks_again(fake_llm):
    english = {"question": "What is the square of the hypotenuse equal to?", "choices": ["a", "b", "c", "d"],
               "answer": "A", "explanation": "It is the sum of the squares of the other sides."}
    spanish = {"question": "¿A qué es igual el cuadrado de la hipotenusa?",
               "choices": ["A la suma de los cuadrados", "Al doble", "A la mitad", "A cero"],
               "answer": "A", "explanation": "Es la suma de los cuadrados de los catetos."}
    llm = fake_llm([json.dumps({"questions": [english]}), json.dumps({"questions": [spanish]})])

    questions = QuizAgent().generate_qcm(COURSES["es"], "beginner", count=1)

    assert [q["question"] for q in questions] == [spanish["question"]]
    assert "LANGUE : espagnol, obligatoire" in llm.prompts[0]


def test_exercise_labels_follow_the_course_language():
    raw = {"title": "Suma", "statement": "Escribir una función suma(a, b).", "language": "python",
           "starter_code": "def suma(a, b):\n    pass", "examples": ["suma(1, 2) -> 3"]}
    question = QuizAgent._clean_exercise(raw, "es")["question"]
    assert "Ejemplos:" in question and "Código inicial:" in question
