"""Prompts pour la génération de QCM et de TP."""

# Règles calculées par le code à partir du contenu (services/course_profile.py), puis vérifiées à la réception
LANGUAGE_RULES = {
    "ar": ("LANGUE : arabe, obligatoire. Écris TOUT en arabe (questions, choix, explications, titres, énoncés, "
           "corrections). Aucune phrase en français ni en anglais."),
    "fr": "LANGUE : la langue principale du contenu (français par défaut, anglais si le cours est en anglais).",
}
EXERCISE_RULES = {
    True: ("TYPE D'EXERCICES : cours de programmation. Exercices de code dans le langage du cours, avec exemples "
           "d'appels et résultats attendus, code de départ et solution."),
    False: ("TYPE D'EXERCICES : ce cours n'est PAS un cours de programmation. AUCUN exercice de code (pas de fonction, "
            "pas de Python, pas d'algorithme informatique). Chaque exercice est de type \"text\" : problèmes, calculs, "
            "explications, comme dans le cours ; starter_code, solution_code et language sont vides."),
}

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
        "TP ouverts : problème en plusieurs étapes, comparaison de méthodes ou erreur à trouver "
        "et corriger, avec justification attendue."
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
{course_rules}

Instructions :
- Génère exactement {count} questions à choix multiples basées UNIQUEMENT sur le contenu ci-dessus.
- Chaque question a exactement 4 choix, une seule bonne réponse.
- Respecte strictement les consignes du niveau ci-dessus.
- "answer" est la lettre de la bonne réponse (A, B, C ou D) dans la liste "choices".
- "explanation" : la correction, en UNE phrase de 15 mots maximum, précise et juste.
- Choix courts : quelques mots, sans répéter la question.
- La question elle-même reste complète et précise (pas de raccourci sur la question).
- Rédige dans la langue imposée ci-dessus (LANGUE), sans mélanger les langues.
- Adapte le vocabulaire et la difficulté au public du cours (par exemple des élèves du primaire).
- Ne pose jamais de question sur le document lui-même (pages, numérotation, format, fichier, PDF) : uniquement sur les notions enseignées.

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
{course_rules}

Instructions :
- Génère exactement {count} exercices pratiques COMPLETS basés sur le contenu ci-dessus, que l'étudiant
  peut faire seul sans autre information.
- Respecte la règle TYPE D'EXERCICES ci-dessus.
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
- Rédige dans la langue imposée ci-dessus (LANGUE), sans mélanger les langues.
- Adapte le vocabulaire et la difficulté au public du cours (par exemple des élèves du primaire).
- Ne pose jamais de question sur le document lui-même (pages, numérotation, format, fichier, PDF) : uniquement sur les notions enseignées.
- Le code peut garder des noms en anglais si le cours les utilise.

Réponds UNIQUEMENT avec ce JSON :
{{
  "exercises": [
    {{
      "title": "Titre court",
      "statement": "Énoncé complet de l'exercice",
      "examples": [],
      "starter_code": "",
      "language": "",
      "solution_code": "",
      "solution_explanation": "Correction détaillée",
      "type": "text"
    }}
  ]
}}
"""
