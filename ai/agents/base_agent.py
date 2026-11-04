"""Classe de base pour tous les agents pédagogiques."""

import json
import re

from agents.llm_client import get_llm_client


class BaseAgent:
    """Classe de base : accès au LLM + parsing JSON robuste."""

    def __init__(self, name: str):
        self.name = name

    @property
    def llm(self):
        return get_llm_client()

    def _call_llm(self, prompt: str, system: str = None, temperature: float = 0.7, json_mode: bool = False) -> str:
        return self.llm.generate(prompt, system=system, temperature=temperature, json_mode=json_mode)

    @staticmethod
    def _parse_json_response(text: str):
        """Extraire du JSON depuis la réponse du LLM (avec fallback robuste)."""
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            pass

        # Bloc ```json ... ```
        code_block = re.search(r"```(?:json)?\s*\n?(.*?)\n?```", text, re.DOTALL)
        if code_block:
            try:
                return json.loads(code_block.group(1).strip())
            except json.JSONDecodeError:
                pass

        # Premier tableau ou objet JSON trouvé
        json_match = re.search(r"(\[.*\]|\{.*\})", text, re.DOTALL)
        if json_match:
            try:
                return json.loads(json_match.group(1))
            except json.JSONDecodeError:
                pass

        return None
