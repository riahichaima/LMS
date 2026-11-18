"""Profil d'un cours, calculé dans le code (pas laissé au modèle) : sa langue et s'il enseigne la programmation.

Langues reconnues :
- par leur écriture : arabe, hébreu, russe (cyrillique), grec, hindi (devanagari), chinois, japonais, coréen ;
- par leurs mots les plus courants (écriture latine) : français, anglais, espagnol, allemand, italien, portugais.
Une langue non reconnue retombe sur le français, la langue de la plateforme.
"""

import re

DEFAULT_LANGUAGE = "fr"

# Nom de chaque langue, tel qu'il apparaît dans la consigne du modèle (« Écris TOUT en arabe »)
LANGUAGE_NAMES = {
    "fr": "français", "en": "anglais", "es": "espagnol", "de": "allemand", "it": "italien", "pt": "portugais",
    "ar": "arabe", "he": "hébreu", "ru": "russe", "el": "grec", "hi": "hindi",
    "zh": "chinois", "ja": "japonais", "ko": "coréen",
}

# Écritures non latines : une lettre de ces plages désigne directement la langue
SCRIPTS = {
    "ar": re.compile(r"[؀-ۿݐ-ݿࢠ-ࣿﭐ-﷿ﹰ-﻿]"),
    "he": re.compile(r"[֐-׿יִ-ﭏ]"),
    "ru": re.compile(r"[Ѐ-ӿ]"),
    "el": re.compile(r"[Ͱ-Ͽἀ-῿]"),
    "hi": re.compile(r"[ऀ-ॿ]"),
    "ja": re.compile(r"[぀-ヿ]"),  # hiragana et katakana (les kanji sont comptés avec le chinois)
    "ko": re.compile(r"[가-힯ᄀ-ᇿ]"),
    "zh": re.compile(r"[一-鿿㐀-䶿]"),
}

# Mots très fréquents et assez distinctifs de chaque langue à écriture latine
STOPWORDS = {
    "fr": {"le", "les", "des", "est", "et", "une", "dans", "pour", "qui", "du", "sur", "avec", "pas", "nous",
           "vous", "ce", "cette", "sont", "au", "aux", "ou", "on", "il", "elle"},
    "en": {"the", "and", "is", "of", "to", "that", "for", "with", "are", "this", "it", "on", "be", "you",
           "we", "an", "can", "from", "which", "or", "by", "was", "what", "how"},
    "es": {"el", "los", "las", "del", "y", "es", "una", "por", "con", "para", "como", "lo", "su", "al",
           "pero", "más", "este", "esta", "son", "entre", "cuando", "muy", "sin"},
    "de": {"der", "die", "das", "und", "ist", "nicht", "ein", "eine", "zu", "mit", "den", "von", "für",
           "auf", "sich", "dem", "im", "auch", "wird", "werden", "oder", "wie", "sind"},
    "it": {"il", "di", "che", "e", "per", "una", "non", "sono", "con", "della", "gli", "nel", "è", "un",
           "alla", "anche", "come", "questo", "questa", "più", "dei", "delle"},
    "pt": {"os", "não", "uma", "com", "do", "da", "em", "é", "um", "dos", "no", "na", "das", "ao", "pelo",
           "pela", "também", "são", "mais", "isso", "esta", "você"},
}
_WORD = re.compile(r"[^\W\d_]+", re.UNICODE)
_CODE_BLOCK = re.compile(r"```.*?```", re.DOTALL)

# Indices de code : définitions de fonctions, mots-clés, blocs Markdown, appels typiques
_CODE_HINTS = re.compile(
    r"```|\bdef\s+\w+\s*\(|\bfunction\s+\w+\s*\(|\bclass\s+\w+\s*[:{(]|\bpublic\s+(static|class|void)\b|"
    r"#include\b|\bimport\s+[\w.]+|\bSELECT\b[^\n]{0,80}\bFROM\b|console\.log\(|System\.out\.|\bprint\s*\(|"
    r"\breturn\b[^\n]{0,40};|=>|\bvar\s+\w+\s*=|\blet\s+\w+\s*=|\bconst\s+\w+\s*=",
    re.IGNORECASE,
)
MIN_CODE_HINTS = 3
MIN_STOPWORD_HITS = 3


def _letters(text: str) -> list[str]:
    return [c for c in text if c.isalpha()]


def script_ratio(text: str, language: str) -> float:
    """Part des lettres écrites dans l'écriture de `language` (0 si aucune lettre)."""
    letters = _letters(text)
    if not letters:
        return 0.0
    pattern = SCRIPTS[language]
    if language == "ja":  # le japonais mêle kana et kanji
        return sum(1 for c in letters if pattern.match(c) or SCRIPTS["zh"].match(c)) / len(letters)
    return sum(1 for c in letters if pattern.match(c)) / len(letters)


def arabic_ratio(text: str) -> float:
    return script_ratio(text, "ar")


def _script_language(text: str) -> str | None:
    """Langue à écriture non latine qui représente au moins 30 % des lettres.

    Seuil bas : les titres des ressources et les repères « [Pages 1-2] » ajoutés par la plateforme sont en
    caractères latins, même pour un cours entièrement en arabe ou en russe.
    """
    letters = _letters(text)
    if not letters:
        return None
    counts = {lang: sum(1 for c in letters if pattern.match(c)) for lang, pattern in SCRIPTS.items()}
    if counts["ja"]:  # kana présents : les kanji sont japonais
        counts["ja"] += counts["zh"]
        counts["zh"] = 0
    lang, best = max(counts.items(), key=lambda kv: kv[1])
    return lang if best / len(letters) >= 0.3 else None


def _stopword_scores(text: str) -> dict[str, int]:
    words = [w.lower() for w in _WORD.findall(text)]
    return {lang: sum(1 for w in words if w in stop) for lang, stop in STOPWORDS.items()}


def _latin_language(text: str) -> str | None:
    scores = _stopword_scores(text)
    lang, best = max(scores.items(), key=lambda kv: kv[1])
    return lang if best >= MIN_STOPWORD_HITS else None


def course_language(content: str) -> str:
    """Code de la langue principale du cours (« ar », « en », « es »…), « fr » par défaut."""
    return _script_language(content) or _latin_language(content) or DEFAULT_LANGUAGE


def language_name(language: str) -> str:
    return LANGUAGE_NAMES.get(language, LANGUAGE_NAMES[DEFAULT_LANGUAGE])


def is_in_language(text: str, language: str) -> bool:
    """Un texte généré est-il bien dans la langue du cours ?

    Le code entre ``` est ignoré (mots-clés anglais), ainsi que les nombres, symboles et unités.
    Un texte trop court pour être jugé est accepté.
    """
    prose = _CODE_BLOCK.sub(" ", text)
    if language in SCRIPTS:
        return script_ratio(prose, language) >= 0.6
    if _script_language(prose):  # langue latine attendue, mais texte en arabe, russe...
        return False
    scores = _stopword_scores(prose)
    best_lang, best = max(scores.items(), key=lambda kv: kv[1])
    if best < 2 or best_lang == language:
        return True
    return scores.get(language, 0) >= best


def is_programming_course(content: str) -> bool:
    """Le cours enseigne-t-il la programmation ? (au moins 3 indices de code dans le contenu)"""
    return len(_CODE_HINTS.findall(content)) >= MIN_CODE_HINTS
