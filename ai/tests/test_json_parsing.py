"""Lecture robuste du JSON renvoyé par le modèle (QCM, TP, corrections)."""

from agents.base_agent import BaseAgent

parse = BaseAgent._parse_json_response


def test_json_direct():
    assert parse('{"questions": []}') == {"questions": []}


def test_json_in_markdown_block():
    text = 'Voici le quiz :\n```json\n{"questions": [{"question": "Q1"}]}\n```\nBonne chance !'
    assert parse(text) == {"questions": [{"question": "Q1"}]}


def test_json_surrounded_by_text():
    assert parse('Résultat : [1, 2, 3] fin') == [1, 2, 3]


def test_invalid_json_returns_none():
    assert parse("Je ne peux pas répondre.") is None
    assert parse("{ pas du json }") is None
