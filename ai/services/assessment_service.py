"""Génération des quiz / TP demandés par le backend, au format attendu par Spring Boot."""

import logging
from concurrent.futures import ThreadPoolExecutor

from agents.quiz_agent import QuizAgent
from api.schemas import DEFAULT_LEVELS, GenerateAssessmentsRequest
from services.resource_loader import build_course_content

logger = logging.getLogger(__name__)

QUIZ_TIME_LIMIT_MINUTES = 15
QUIZ_PASSING_SCORE = 60.0
TP_TIME_LIMIT_HOURS = 24
TP_MAX_POINTS = 100.0


def generate_assessments(request: GenerateAssessmentsRequest) -> dict:
    """1) extraire le contenu des ressources, 2) générer quiz/TP par niveau en parallèle."""
    levels = request.difficulty_levels or DEFAULT_LEVELS
    content = build_course_content(request.lesson_descriptions, request.resource_details)
    if not content.strip():
        raise ValueError("Aucun contenu exploitable : vérifiez les PDF, vidéos ou liens envoyés.")
    logger.info("Contenu du cours extrait : %d caractères", len(content))

    agent = QuizAgent()
    # Azure supporte le parallélisme : tous les quiz/TP de la demande sont générés en même temps
    workers = len(levels) * 2
    with ThreadPoolExecutor(max_workers=workers) as executor:
        quiz_futures = [
            executor.submit(_build_quiz, agent, content, level, request.quiz_question_count)
            for level in levels
        ] if request.generate_quiz else []
        tp_futures = [
            executor.submit(_build_assignment, agent, content, level, request.assignment_exercise_count)
            for level in levels
        ] if request.generate_assignment else []

        return {
            "quizzes": [f.result() for f in quiz_futures],
            "assignments": [f.result() for f in tp_futures],
        }


def _build_quiz(agent: QuizAgent, content: str, level: str, count: int) -> dict:
    try:
        questions = agent.generate_qcm(content, level.lower(), count)
    except Exception:
        logger.exception("Échec de génération du quiz %s", level)
        questions = []

    mapped = [
        {
            "question_text": q["question"],
            "question_type": "MCQ",
            "correct_answer": q["choices"][q["correct_index"]],
            "points": 1,
            "question_order": i + 1,
            "explanation_text": q["explanation"],
            "answers": [
                {"answer_text": choice, "is_correct": j == q["correct_index"], "answer_order": j + 1}
                for j, choice in enumerate(q["choices"])
            ],
        }
        for i, q in enumerate(questions)
    ]
    return {
        "title": f"Quiz {level}",
        "difficulty_level": level,
        "question_count": len(mapped),
        "time_limit_minutes": QUIZ_TIME_LIMIT_MINUTES,
        "passing_score": QUIZ_PASSING_SCORE,
        "questions": mapped,
    }


def _build_assignment(agent: QuizAgent, content: str, level: str, count: int) -> dict:
    try:
        exercises = agent.generate_tp(content, level.lower(), count)
    except Exception:
        logger.exception("Échec de génération du TP %s", level)
        exercises = []

    return {
        "title": f"TP {level}",
        "difficulty_level": level,
        "exercise_count": len(exercises),
        "time_limit_hours": TP_TIME_LIMIT_HOURS,
        "max_points": TP_MAX_POINTS,
        "exercises": exercises,
    }
