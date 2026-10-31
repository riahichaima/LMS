"""Prompts pour l'Agent 4 — Correction Automatique."""

TEXT_CORRECTION_PROMPT = """Tu es un correcteur pédagogique expert et bienveillant.

QUESTION POSÉE :
{question}

RÉPONSE DE L'ÉTUDIANT :
{student_answer}

RÉPONSE ATTENDUE / CRITÈRES :
{expected_answer}

NIVEAU DE L'ÉTUDIANT : {level}

Instructions :
- Analyse la réponse de l'étudiant par rapport à la réponse attendue.
- Évalue la qualité, la précision et la complétude.

IMPORTANT : Réponds UNIQUEMENT avec un JSON valide, sans texte avant ou après.
Format EXACT attendu :
{{
  "score": 75,
  "feedback": "Retour détaillé sur la réponse...",
  "errors": ["Erreur 1", "Erreur 2"],
  "correction": "Voici la correction complète..."
}}

Note : le score est sur 100.
"""

CODE_CORRECTION_PROMPT = """Tu es un correcteur de code expert et pédagogique.

QUESTION / ÉNONCÉ :
{question}

CODE DE L'ÉTUDIANT :
```{language}
{student_code}
```

CODE ATTENDU / SOLUTION :
```{language}
{expected_code}
```

Instructions :
- Analyse le code de l'étudiant.
- Vérifie la logique, la syntaxe, l'efficacité et le style.
- Compare avec la solution attendue.
- Identifie les erreurs et propose des améliorations.

IMPORTANT : Réponds UNIQUEMENT avec un JSON valide, sans texte avant ou après.
Format EXACT attendu :
{{
  "score": 70,
  "feedback": "Analyse détaillée du code...",
  "errors": ["Erreur 1 : description", "Erreur 2 : description"],
  "correction": "Code corrigé avec explications..."
}}

Note : le score est sur 100.
"""
