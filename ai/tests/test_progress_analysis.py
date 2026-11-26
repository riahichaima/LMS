"""Analyse de progression : les notes de quiz et de TP arrivent séparément jusqu'à l'agent de recommandation."""

from fastapi.testclient import TestClient

import api.progress_routes as progress_routes
from agents.recommendation_agent import RecommendationAgent
from main import app

client = TestClient(app)


def capture(monkeypatch):
    seen = {}

    def recommend(quiz_scores=None, tp_scores=None):
        seen.update(quiz_scores=quiz_scores, tp_scores=tp_scores)
        return {"level_estimated": "advanced", "analysis": "ok", "recommendations": []}

    monkeypatch.setattr(progress_routes.recommendation_agent, "recommend", recommend)
    return seen


def test_les_tp_rendus_sont_transmis_a_l_agent(monkeypatch):
    seen = capture(monkeypatch)
    r = client.post("/api/progress/analyze", json={"course_id": 1, "average_score": 91.7,
                                                   "quiz_scores": [80.0], "tp_scores": [100.0, 95.0]})
    assert r.status_code == 200
    assert seen == {"quiz_scores": [80.0], "tp_scores": [100.0, 95.0]}


def test_aucun_tp_rendu_reste_signale(monkeypatch):
    seen = capture(monkeypatch)
    client.post("/api/progress/analyze", json={"average_score": 80.0, "quiz_scores": [80.0], "tp_scores": []})
    assert seen == {"quiz_scores": [80.0], "tp_scores": None}


def test_ancien_client_avec_la_seule_moyenne(monkeypatch):
    seen = capture(monkeypatch)
    client.post("/api/progress/analyze", json={"average_score": 72.5})
    assert seen == {"quiz_scores": [72.5], "tp_scores": None}


def test_le_resume_envoye_au_modele_compte_les_tp():
    summary = RecommendationAgent._build_performance_summary([80.0], [100.0, 95.0])
    assert "2 TP soumis" in summary and "Aucun TP" not in summary
