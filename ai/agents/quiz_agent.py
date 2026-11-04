"""Agent de génération de QCM et TP à partir du contenu d'un cours."""

import logging
import re

from agents.base_agent import BaseAgent
from config import settings
from prompts.quiz_prompts import LEVEL_GUIDANCE, QCM_GENERATION_PROMPT, TP_GENERATION_PROMPT

logger = logging.getLogger(__name__)

LETTERS = "ABCD"
# "A. texte", "B) texte", "c - texte"...
_LETTER_PREFIX = re.compile(r"^\s*([A-Da-d])\s*[\.\)\-:]\s*")


class QuizAgent(BaseAgent):
    """Génère des QCM (avec correction) et des TP (avec solution)."""

    MAX_ATTEMPTS = 2

    def __init__(self):
        super().__init__(name="quiz_agent")

    def generate_qcm(self, content: str, level: str, count: int = 5) -> list[dict]:
        """Retourne une liste de questions validées :
        {question, choices: [4 textes], correct_index, explanation}
        """
        questions: list[dict] = []
        for attempt in range(self.MAX_ATTEMPTS):
            missing = count - len(questions)
            if missing <= 0:
                break
            prompt = QCM_GENERATION_PROMPT.format(
                content=content[: settings.max_content_chars], level=level,
                level_guidance=LEVEL_GUIDANCE.get(level, ""), count=missing,
            )
            parsed = self._parse_json_response(self._call_llm(prompt, temperature=0.5, json_mode=True))
            raw = parsed.get("questions", []) if isinstance(parsed, dict) else parsed or []
            valid = [q for q in (self._clean_question(r) for r in raw) if q]
            logger.info("QCM %s : tentative %d -> %d/%d questions valides", level, attempt + 1, len(valid), missing)
            questions.extend(valid)
        return questions[:count]

    def generate_tp(self, content: str, level: str, count: int = 3) -> list[dict]:
        """Retourne une liste d'exercices complets.

        `question` (énoncé + exemples + code de départ) et `expected_solution` (code + explication)
        sont en Markdown avec des blocs ```langage, prêts à être affichés à l'étudiant.
        """
        prompt = TP_GENERATION_PROMPT.format(
            content=content[: settings.max_content_chars], level=level,
            level_guidance=LEVEL_GUIDANCE.get(level, ""), count=count,
        )
        parsed = self._parse_json_response(self._call_llm(prompt, temperature=0.5, json_mode=True))
        raw = parsed.get("exercises", []) if isinstance(parsed, dict) else parsed or []
        return [ex for ex in (self._clean_exercise(r) for r in raw) if ex][:count]

    @staticmethod
    def _clean_exercise(raw) -> dict | None:
        if not isinstance(raw, dict):
            return None
        statement = str(raw.get("statement") or raw.get("question") or "").strip()
        if not statement:
            return None
        title = str(raw.get("title", "")).strip()
        language = str(raw.get("language", "")).strip().lower()
        starter = str(raw.get("starter_code", "")).strip()
        solution_code = str(raw.get("solution_code", "")).strip()
        explanation = str(raw.get("solution_explanation") or raw.get("expected_solution") or "").strip()
        examples = [str(e).strip() for e in raw.get("examples") or [] if str(e).strip()]

        question = [f"**{title}**", ""] if title else []
        question.append(statement)
        if examples:
            question += ["", "Exemples :", f"```{language}", *examples, "```"]
        if starter:
            question += ["", "Code de départ :", f"```{language}", starter, "```"]

        solution = []
        if solution_code:
            solution += [f"```{language}", solution_code, "```", ""]
        solution.append(explanation)

        return {
            "title": title,
            "question": "\n".join(question).strip(),
            "expected_solution": "\n".join(solution).strip(),
            "language": language,
            "type": "code" if raw.get("type") == "code" or solution_code else "text",
        }

    @staticmethod
    def _clean_question(raw) -> dict | None:
        """Valider une question du LLM et trouver l'index de la bonne réponse."""
        if not isinstance(raw, dict):
            return None
        question = str(raw.get("question", "")).strip()
        choices = [_LETTER_PREFIX.sub("", str(c)).strip() for c in raw.get("choices", []) if str(c).strip()]
        if not question or len(choices) < 2:
            return None
        choices = choices[:4]

        answer = str(raw.get("answer", "")).strip()
        correct_index = -1
        letter = _LETTER_PREFIX.match(answer + ".") if len(answer) <= 2 else _LETTER_PREFIX.match(answer)
        if letter:
            correct_index = LETTERS.index(letter.group(1).upper())
        else:
            # Le modèle a parfois mis le texte de la réponse au lieu de la lettre
            normalized = answer.lower()
            for i, c in enumerate(choices):
                if c.lower() == normalized:
                    correct_index = i
                    break
        if not 0 <= correct_index < len(choices):
            return None

        return {
            "question": question,
            "choices": choices,
            "correct_index": correct_index,
            "explanation": str(raw.get("explanation", "")).strip(),
        }
