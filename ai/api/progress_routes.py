"""Analyse de progression — appelée par Spring Boot (ProgressAnalysisService)."""

import logging

from fastapi import APIRouter

from agents.correction_agent import recommend_level
from agents.recommendation_agent import RecommendationAgent
from api.schemas import ProgressAnalysisRequest

logger = logging.getLogger(__name__)
router = APIRouter()
recommendation_agent = RecommendationAgent()


@router.post("/analyze")
def analyze_progress(request: ProgressAnalysisRequest):
    average_score = request.average_score or 0.0

    try:
        result = recommendation_agent.recommend(quiz_scores=[average_score] if average_score > 0 else None)
        recommendations = result.get("recommendations", [])
        analysis = result.get("analysis", "")
    except Exception:
        logger.exception("Échec de l'agent de recommandation")
        recommendations = []
        analysis = "Analyse basée sur les statistiques disponibles."

    return {
        "recommended_lessons": [r.get("title", "") for r in recommendations if isinstance(r, dict)],
        "weak_areas": ["Concepts de base à renforcer"] if average_score < 50 else [],
        "strong_areas": [] if average_score < 50 else ["Bonne compréhension des fondamentaux"],
        "personalized_advice": analysis,
        "recommended_level": recommend_level(average_score),
    }
