"""Configuration des tests : aucun appel réel à Azure (modèle simulé)."""

import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))


class FakeLLM:
    """Modèle simulé : renvoie les réponses prévues dans l'ordre et mémorise les prompts."""

    def __init__(self, responses=None, available=True):
        self.responses = list(responses or [])
        self.prompts = []
        self.available = available

    def generate(self, prompt, system=None, temperature=0.7, json_mode=False):
        self.prompts.append(prompt)
        return self.responses.pop(0) if self.responses else "{}"

    def is_available(self):
        return self.available


@pytest.fixture
def fake_llm(monkeypatch):
    """Remplace le client Azure par un modèle simulé dans les agents et l'API."""
    def install(responses=None, available=True):
        llm = FakeLLM(responses, available)
        monkeypatch.setattr("agents.base_agent.get_llm_client", lambda: llm)
        monkeypatch.setattr("main.get_llm_client", lambda: llm)
        return llm
    return install
