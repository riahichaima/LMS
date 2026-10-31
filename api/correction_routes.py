"""Correction des TP et des quiz — appelée par Spring Boot (AssessmentService)."""

import json

from fastapi import APIRouter, HTTPException

from agents.correction_agent import CorrectionAgent, recommend_level
from api.schemas import QuizCorrectionRequest, TPCorrectionRequest

router = APIRouter()
correction_agent = CorrectionAgent()


@router.post("/")
def correct_tp(request: TPCorrectionRequest):
    """Corriger une réponse de TP (texte ou code) avec feedback détaillé."""
    try:
        return correction_agent.correct_tp(
            question=request.question,
            student_answer=request.student_answer,
            expected_answer=request.expected_answer,
            response_type=request.response_type,
            level=request.level,
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Erreur de correction : {e}")


@router.post("/correct-quiz")
def correct_quiz(request: QuizCorrectionRequest):
    """Corriger un quiz QCM par comparaison avec les bonnes réponses."""
    answers = request.student_answers
    if isinstance(answers, str):
        try:
            answers = json.loads(answers)
        except json.JSONDecodeError:
            answers = {}
    if not isinstance(answers, dict):
        answers = {}

    result = CorrectionAgent.grade_quiz(request.questions, answers)
    return {
        "score": result["score"],
        "max_score": 100.0,
        "feedback": f"Vous avez obtenu {result['correct']}/{result['total']} bonnes réponses ({result['score']:.0f}%).",
        "detailed_explanation": json.dumps(result["details"], ensure_ascii=False),
        "recommended_level": recommend_level(result["score"]),
    }
