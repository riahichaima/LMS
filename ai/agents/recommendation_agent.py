"""Agent de recommandation pédagogique à partir des performances."""

from agents.base_agent import BaseAgent
from prompts.recommendation_prompts import RECOMMENDATION_PROMPT


class RecommendationAgent(BaseAgent):
    """Analyse les scores d'un étudiant et propose des ressources."""

    def __init__(self):
        super().__init__(name="recommendation_agent")

    def recommend(self, quiz_scores: list[float] = None, tp_scores: list[float] = None) -> dict:
        """Retourne {level_estimated, analysis, recommendations: [{type, title, description, priority}]}."""
        prompt = RECOMMENDATION_PROMPT.format(
            performance_summary=self._build_performance_summary(quiz_scores, tp_scores)
        )
        response = self._call_llm(prompt, temperature=0.5, json_mode=True)
        parsed = self._parse_json_response(response)

        if isinstance(parsed, dict) and "level_estimated" in parsed:
            return parsed

        return {
            "level_estimated": "intermediate",
            "analysis": response,
            "recommendations": [
                {
                    "type": "cours",
                    "title": "Revoir les bases",
                    "description": "Recommandation basée sur l'analyse de vos performances.",
                    "priority": "haute",
                }
            ],
        }

    @staticmethod
    def _build_performance_summary(quiz_scores: list[float] = None, tp_scores: list[float] = None) -> str:
        parts = []
        if quiz_scores:
            avg = sum(quiz_scores) / len(quiz_scores)
            parts.append(f"Scores QCM : {len(quiz_scores)} quiz réalisés, moyenne {avg:.1f}/100. Détail : {quiz_scores}")
        else:
            parts.append("Aucun QCM réalisé.")
        if tp_scores:
            avg = sum(tp_scores) / len(tp_scores)
            parts.append(f"Scores TP : {len(tp_scores)} TP soumis, moyenne {avg:.1f}/100. Détail : {tp_scores}")
        else:
            parts.append("Aucun TP soumis.")
        return "\n".join(parts)
