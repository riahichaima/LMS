"""Profil d'un cours, calculé dans le code (pas laissé au modèle) : sa langue et s'il enseigne la programmation."""

import re

# Lettres arabes (bloc principal, supplément, formes de présentation)
_ARABIC = re.compile(r"[؀-ۿݐ-ݿࢠ-ࣿﭐ-﷿ﹰ-﻿]")
# Indices de code : définitions de fonctions, mots-clés, blocs Markdown, appels typiques
_CODE_HINTS = re.compile(
    r"```|\bdef\s+\w+\s*\(|\bfunction\s+\w+\s*\(|\bclass\s+\w+\s*[:{(]|\bpublic\s+(static|class|void)\b|"
    r"#include\b|\bimport\s+[\w.]+|\bSELECT\b[^\n]{0,80}\bFROM\b|console\.log\(|System\.out\.|\bprint\s*\(|"
    r"\breturn\b[^\n]{0,40};|=>|\bvar\s+\w+\s*=|\blet\s+\w+\s*=|\bconst\s+\w+\s*=",
    re.IGNORECASE,
)
MIN_CODE_HINTS = 3


def arabic_ratio(text: str) -> float:
    """Part des lettres arabes parmi toutes les lettres (0 si aucune lettre)."""
    letters = [c for c in text if c.isalpha()]
    if not letters:
        return 0.0
    return sum(1 for c in letters if _ARABIC.match(c)) / len(letters)


def course_language(content: str) -> str:
    """« ar » si le cours est surtout en arabe, sinon « fr ».

    Seuil bas (30 %) : les titres des ressources et les repères « [Pages 1-2] » ajoutés par la plateforme
    sont en caractères latins, même pour un cours entièrement en arabe.
    """
    return "ar" if arabic_ratio(content) >= 0.3 else "fr"


def is_in_language(text: str, language: str) -> bool:
    """Un texte généré est-il bien dans la langue du cours ? (symboles, nombres et unités comme « cm » tolérés)"""
    if language != "ar":
        return True
    return arabic_ratio(text) >= 0.6


def is_programming_course(content: str) -> bool:
    """Le cours enseigne-t-il la programmation ? (au moins 3 indices de code dans le contenu)"""
    return len(_CODE_HINTS.findall(content)) >= MIN_CODE_HINTS
