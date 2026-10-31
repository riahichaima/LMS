"""Agent assistant : répond aux questions des étudiants sur un contenu (PDF, vidéo, lien)."""

from agents.base_agent import BaseAgent
from config import settings
from prompts.tutor_prompts import TUTOR_QUESTION_PROMPT, TUTOR_SYSTEM_PROMPT


class TutorAgent(BaseAgent):
    def __init__(self):
        super().__init__(name="tutor_agent")

    def answer(self, content: str, question: str, level: str = "intermediate", content_type: str = "texte") -> str:
        prompt = TUTOR_QUESTION_PROMPT.format(
            content=content[: settings.max_content_chars],
            content_type=content_type,
            question=question,
            level=level,
        )
        return self._call_llm(prompt, system=TUTOR_SYSTEM_PROMPT, temperature=0.4)
