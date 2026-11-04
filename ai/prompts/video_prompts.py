"""Prompts pour l'analyse des vidéos."""

FRAMES_DESCRIPTION_PROMPT = """Voici {count} images extraites à intervalles réguliers d'une vidéo de cours, dans l'ordre chronologique.

Décris en français uniquement le contenu pédagogique visible : titres et texte des slides, code affiché,
schémas, formules, tableaux, commandes ou résultats à l'écran. Recopie fidèlement le texte et le code lisibles.
Ignore les visages, l'arrière-plan et les éléments décoratifs. Si une image n'a rien de pédagogique, passe-la.

Réponds sous forme de liste, une entrée par image : "Image N : ..."."""
