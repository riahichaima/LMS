"""Agent de correction : TP (texte/code, via LLM) et QCM (comparaison directe)."""

import re

from agents.base_agent import BaseAgent
from prompts.correction_prompts import CODE_CORRECTION_PROMPT, TEXT_CORRECTION_PROMPT

_LETTER_PREFIX = re.compile(r"^\s*[A-Da-d]\s*[\.\)\-:]\s*")


def _normalize(answer) -> str:
    return _LETTER_PREFIX.sub("", str(answer or "")).strip().lower()


def recommend_level(score: float) -> str:
    if score >= 80:
        return "ADVANCED"
    if score >= 50:
        return "INTERMEDIATE"
    return "BEGINNER"


class CorrectionAgent(BaseAgent):
    """Correcteur automatique des réponses étudiantes."""

    def __init__(self):
        super().__init__(name="correction_agent")

    def correct_tp(
        self,
        question: str,
        student_answer: str,
        expected_answer: str,
        response_type: str = "text",
        level: str = "intermediate",
    ) -> dict:
        """Corriger une réponse de TP. Retourne {score, feedback, errors, correction}."""
        if response_type == "code":
            prompt = CODE_CORRECTION_PROMPT.format(
                question=question, student_code=student_answer, expected_code=expected_answer, language=""
            )
        else:
            prompt = TEXT_CORRECTION_PROMPT.format(
                question=question, student_answer=student_answer, expected_answer=expected_answer, level=level
            )
        response = self._call_llm(prompt, temperature=0.3, json_mode=True)
        parsed = self._parse_json_response(response)

        if isinstance(parsed, dict) and "score" in parsed:
            return {
                "score": float(parsed.get("score") or 0),
                "feedback": str(parsed.get("feedback", "")),
                "errors": [str(e) for e in parsed.get("errors", []) or []],
                "correction": str(parsed.get("correction", "")),
            }
        return {"score": 0.0, "feedback": response, "errors": [], "correction": expected_answer}

    @staticmethod
    def grade_quiz(questions: list[dict], student_answers: dict) -> dict:
        """Corriger un QCM. `student_answers` = {"1": "texte réponse", "2": ...} (ordre des questions)."""
        total = len(questions)
        correct_count = 0
        details = []

        for i, q in enumerate(questions):
            expected = q.get("correct_answer", "")
            given = student_answers.get(str(i + 1), "")
            is_correct = bool(given) and _normalize(given) == _normalize(expected)
            correct_count += is_correct
            item = {
                "question": q.get("question_text", f"Q{i + 1}"),
                "correct": is_correct,
                "student_answer": given,
                "expected": expected,
            }
            if not is_correct:
                item["explanation"] = q.get("explanation_text", "")
            details.append(item)

        score = round(correct_count / total * 100, 1) if total else 0.0
        return {"score": float(score), "correct": correct_count, "total": total, "details": details}
