"""Prompts pour la génération de QCM et de TP."""

# Consignes injectées selon le niveau choisi par le professeur
LEVEL_GUIDANCE = {
    "beginner": (
        "Consignes niveau débutant : questions de compréhension directe (définitions, rôle d'un "
        "élément, reconnaissance), vocabulaire simple, mauvais choix clairement distincts. "
        "TP guidés pas à pas, une seule notion par exercice."
    ),
    "intermediate": (
        "Consignes niveau intermédiaire : questions d'application (que se passe-t-il si..., quel "
        "résultat donne..., quelle méthode utiliser), mauvais choix plausibles. "
        "TP qui combinent deux ou trois notions du cours."
    ),
    "advanced": (
        "Consignes niveau avancé : questions d'analyse (cas limites, erreurs à détecter, comparaison "
        "de solutions, justification), mauvais choix très proches de la bonne réponse. "
        "TP ouverts : conception, optimisation ou débogage, avec justification attendue."
    ),
}

# Préfixe commun à tous les prompts de génération : le contenu du cours vient en premier et
# reste identique d'un appel à l'autre, ce qui permet à Azure OpenAI de réutiliser son cache
# de prompt (moins de latence et de coût pour les quiz/TP d'une même demande).
COURSE_CONTENT_PREFIX = """CONTENU DU COURS (extrait des PDF, vidéos et liens fournis par le professeur) :
{content}

"""

QCM_GENERATION_PROMPT = COURSE_CONTENT_PREFIX + """Tu es un professeur qui prépare un QCM pour ses étudiants à partir du contenu ci-dessus.

NIVEAU : {level}
{level_guidance}
NOMBRE DE QUESTIONS : {count}

Instructions :
- Génère exactement {count} questions à choix multiples basées UNIQUEMENT sur le contenu ci-dessus.
- Chaque question a exactement 4 choix, une seule bonne réponse.
- Respecte strictement les consignes du niveau ci-dessus.
- "answer" est la lettre de la bonne réponse (A, B, C ou D) dans la liste "choices".
- "explanation" : la correction, en UNE phrase de 15 mots maximum, précise et juste.
- Choix courts : quelques mots, sans répéter la question.
- La question elle-même reste complète et précise (pas de raccourci sur la question).
- Rédige en français.

Réponds UNIQUEMENT avec ce JSON :
{{
  "questions": [
    {{
      "question": "La question ici ?",
      "choices": ["Premier choix", "Deuxième choix", "Troisième choix", "Quatrième choix"],
      "answer": "B",
      "explanation": "Explication de la bonne réponse."
    }}
  ]
}}
"""

TP_GENERATION_PROMPT = COURSE_CONTENT_PREFIX + """Tu es un professeur qui prépare un TP (travaux pratiques) pour ses étudiants à partir du contenu ci-dessus.

NIVEAU : {level}
{level_guidance}
NOMBRE D'EXERCICES : {count}

Instructions :
- Génère exactement {count} exercices pratiques COMPLETS basés sur le contenu ci-dessus, que l'étudiant
  peut faire seul sans autre information.
- Si le cours porte sur la programmation, les exercices sont des exercices de code, dans le langage du cours.
  Exemple d'exercice attendu : "Écrire une fonction verifier_longueur(chaine) qui retourne True si la chaîne
  ne dépasse pas 5 caractères, sinon False", avec des exemples d'appels et les résultats attendus.
- Pour chaque exercice :
  * "title" : titre court.
  * "statement" : énoncé détaillé (contexte, ce qu'il faut faire, contraintes).
  * "examples" : 1 à 3 exemples concrets "entrée -> sortie attendue" (liste vide si non pertinent).
  * "starter_code" : squelette de code à compléter (signature de fonction, TODO), "" si exercice non-code.
  * "language" : langage du code ("python", "java", "javascript", "sql"...), "" si exercice non-code.
  * "solution_code" : solution complète et correcte, qui respecte les exemples, "" si exercice non-code.
  * "solution_explanation" : explication de la solution en 2 à 5 phrases (c'est la correction).
  * "type" : "code" pour un exercice de programmation, sinon "text".
- Respecte strictement les consignes du niveau ci-dessus.
- Rédige en français (le code peut garder des noms en anglais si le cours les utilise).

Réponds UNIQUEMENT avec ce JSON :
{{
  "exercises": [
    {{
      "title": "Vérifier la longueur d'une chaîne",
      "statement": "Écrire une fonction ...",
      "examples": ["verifier_longueur(\\"abc\\") -> True", "verifier_longueur(\\"abcdef\\") -> False"],
      "starter_code": "def verifier_longueur(chaine):\\n    # TODO\\n    pass",
      "language": "python",
      "solution_code": "def verifier_longueur(chaine):\\n    return len(chaine) <= 5",
      "solution_explanation": "La fonction compare la longueur ...",
      "type": "code"
    }}
  ]
}}
"""
