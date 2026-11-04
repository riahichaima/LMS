"""Agent assistant : répond aux questions des étudiants sur un contenu (PDF, vidéo, lien)."""

from agents.base_agent import BaseAgent
from config import settings
from prompts.tutor_prompts import (
    COURSE_CHAT_SYSTEM_PROMPT,
    COURSE_SUMMARY_REQUEST,
    TUTOR_QUESTION_PROMPT,
    TUTOR_SYSTEM_PROMPT,
)

MAX_HISTORY_MESSAGES = 12


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

    def chat_about_course(
        self,
        content: str,
        course_title: str,
        messages: list[dict],
        lesson_title: str | None = None,
        summary: bool = False,
    ) -> str:
        """Chatbot du cours : conversation avec l'historique, ou résumé du cours / de la leçon."""
        focus = f"la leçon « {lesson_title} »" if lesson_title else "le cours en entier"
        system = COURSE_CHAT_SYSTEM_PROMPT.format(
            course_title=course_title, focus=focus, content=content[: settings.max_content_chars]
        )
        history = [
            {"role": m["role"], "content": str(m["content"])}
            for m in messages
            if m.get("role") in ("user", "assistant") and m.get("content")
        ][-MAX_HISTORY_MESSAGES:]
        if summary:
            target = f"la leçon « {lesson_title} »" if lesson_title else f"tout le cours « {course_title} »"
            history.append({"role": "user", "content": COURSE_SUMMARY_REQUEST.format(target=target)})
        if not history:
            raise ValueError("Aucune question à traiter.")
        return self.llm.chat([{"role": "system", "content": system}, *history], temperature=0.4)
