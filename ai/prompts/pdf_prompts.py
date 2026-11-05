"""Prompt de lecture des PDF scannés."""

PDF_OCR_PROMPT = """Voici les pages {first} à {last} d'un support de cours scanné, dans l'ordre.

Recopie fidèlement, en conservant la langue d'origine, tout le contenu pédagogique : titres, paragraphes,
listes, définitions, formules, code, légendes et contenu des tableaux (une ligne par rangée).
Décris brièvement les schémas utiles à la compréhension. Ignore les en-têtes et pieds de page répétitifs,
les numéros de page et les éléments décoratifs. N'invente rien : si un passage est illisible, écris [illisible].

Réponds uniquement avec le texte recopié, page par page, en commençant chaque page par "Page N :"."""
