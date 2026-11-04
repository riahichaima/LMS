"""Correction des QCM, niveau recommandé et endpoints de l'API (sans appel au modèle)."""

from fastapi.testclient import TestClient

from agents.correction_agent import CorrectionAgent, recommend_level
from main import app

client = TestClient(app)

QUESTIONS = [
    {"question_text": "2 + 2 ?", "correct_answer": "4", "explanation_text": "Addition"},
    {"question_text": "Capitale ?", "correct_answer": "Tunis", "explanation_text": "Géographie"},
    {"question_text": "Couleur du ciel ?", "correct_answer": "Bleu", "explanation_text": "Observation"},
    {"question_text": "Langage du backend ?", "correct_answer": "Java", "explanation_text": "Spring Boot"},
]


def test_grade_quiz_score_and_details():
    result = CorrectionAgent.grade_quiz(QUESTIONS, {"1": "B) 4", "2": "tunis", "3": "Vert"})
    assert result["correct"] == 2 and result["total"] == 4
    assert result["score"] == 50.0
    wrong = result["details"][2]
    assert wrong["correct"] is False and wrong["explanation"] == "Observation"
    assert result["details"][3]["student_answer"] == ""  # question sans réponse


def test_grade_quiz_without_questions():
    assert CorrectionAgent.grade_quiz([], {})["score"] == 0.0


def test_recommended_level_thresholds():
    assert recommend_level(80) == "ADVANCED"
    assert recommend_level(79.9) == "INTERMEDIATE"
    assert recommend_level(50) == "INTERMEDIATE"
    assert recommend_level(49.9) == "BEGINNER"


def test_liveness_does_not_call_the_model(fake_llm):
    llm = fake_llm()
    response = client.get("/api/live")
    assert response.status_code == 200 and response.json() == {"status": "ok"}
    assert llm.prompts == []


def test_health_reports_model_status(fake_llm):
    fake_llm(available=False)
    body = client.get("/api/health").json()
    assert body["status"] == "ok" and body["model_available"] is False


def test_correct_quiz_endpoint_accepts_json_string():
    response = client.post("/api/correct/correct-quiz", json={
        "student_answers": '{"1": "4", "2": "Tunis", "3": "Bleu", "4": "Java"}',
        "questions": QUESTIONS,
    })
    assert response.status_code == 200
    body = response.json()
    assert body["score"] == 100.0 and body["max_score"] == 100.0


def test_correct_quiz_endpoint_tolerates_bad_answers():
    response = client.post("/api/correct/correct-quiz", json={"student_answers": "pas du json", "questions": QUESTIONS})
    assert response.status_code == 200 and response.json()["score"] == 0.0
