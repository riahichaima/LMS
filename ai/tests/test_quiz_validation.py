"""Validation des QCM et TP générés par le modèle (structure JSON, bonne réponse, nouvelles tentatives)."""

import json

from agents.quiz_agent import QuizAgent

clean_question = QuizAgent._clean_question
clean_exercise = QuizAgent._clean_exercise


def test_question_with_letter_answer():
    q = clean_question({"question": "2 + 2 ?", "choices": ["A) 3", "B) 4", "C) 5", "D) 22"], "answer": "B", "explanation": "Addition"})
    assert q == {"question": "2 + 2 ?", "choices": ["3", "4", "5", "22"], "correct_index": 1, "explanation": "Addition"}


def test_question_with_text_answer():
    q = clean_question({"question": "Capitale de la Tunisie ?", "choices": ["Sfax", "Tunis", "Sousse"], "answer": "tunis"})
    assert q["correct_index"] == 1


def test_question_keeps_four_choices_max():
    q = clean_question({"question": "Q", "choices": ["a", "b", "c", "d", "e"], "answer": "A"})
    assert len(q["choices"]) == 4


def test_invalid_questions_are_rejected():
    assert clean_question("texte") is None
    assert clean_question({"question": "", "choices": ["a", "b"], "answer": "A"}) is None
    assert clean_question({"question": "Q", "choices": ["seul"], "answer": "A"}) is None
    assert clean_question({"question": "Q", "choices": ["a", "b"], "answer": "D"}) is None  # lettre hors des choix
    assert clean_question({"question": "Q", "choices": ["a", "b"], "answer": "z"}) is None


def test_exercise_is_rendered_as_markdown():
    ex = clean_exercise({
        "title": "Somme", "statement": "Écrire une fonction somme(a, b).", "language": "python",
        "starter_code": "def somme(a, b):\n    pass", "solution_code": "def somme(a, b):\n    return a + b",
        "solution_explanation": "On renvoie a + b.", "examples": ["somme(1, 2) -> 3"],
    })
    assert ex["question"].startswith("**Somme**")
    assert "```python" in ex["question"] and "somme(1, 2) -> 3" in ex["question"]
    assert "return a + b" in ex["expected_solution"] and ex["expected_solution"].endswith("On renvoie a + b.")


def test_exercise_without_statement_is_rejected():
    assert clean_exercise({"title": "Vide"}) is None
    assert clean_exercise(None) is None


def test_generate_qcm_retries_until_count(fake_llm):
    valid = {"question": "Q{}", "choices": ["a", "b", "c", "d"], "answer": "A", "explanation": "e"}
    first = {"questions": [dict(valid, question="Q1"), {"question": "invalide"}, dict(valid, question="Q2")]}
    second = {"questions": [dict(valid, question="Q3")]}
    llm = fake_llm([json.dumps(first), json.dumps(second)])

    questions = QuizAgent().generate_qcm("Contenu du cours", "beginner", count=3)

    assert [q["question"] for q in questions] == ["Q1", "Q2", "Q3"]
    assert len(llm.prompts) == 2  # une 2e tentative pour la question manquante


def test_generate_qcm_handles_unusable_response(fake_llm):
    fake_llm(["pas de JSON", "toujours pas"])
    assert QuizAgent().generate_qcm("Contenu", "advanced", count=2) == []
