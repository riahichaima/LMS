"""RAG de l'administration : rechercher dans le contenu de TOUS les cours, puis répondre en citant les sources.

Pourquoi ici et pas pour les quiz : un quiz doit couvrir toute la leçon, qui tient dans le contexte du modèle
(contenu complet, pas de RAG). L'administration, elle, interroge l'ensemble des cours : c'est trop grand pour
le contexte du modèle, il faut d'abord retrouver les bons passages.

1. Indexation : le texte de chaque leçon et de chaque ressource (PDF, vidéo déjà analysée, lien…) est découpé
   en morceaux d'environ 1 200 caractères qui se chevauchent de 200 caractères.
2. Vecteurs : chaque morceau reçoit un embedding Azure (normalisé). Sans déploiement d'embeddings,
   la recherche se fait par mots-clés (BM25) : la fonction reste utilisable.
3. Question : les morceaux les plus proches (similarité cosinus, ou score BM25) sont donnés au modèle,
   qui répond uniquement à partir d'eux et cite ses sources [1], [2]…
"""

import json
import logging
import math
import re
import threading
import unicodedata
from array import array
from collections import Counter
from datetime import datetime, timezone

from agents.llm_client import get_llm_client
from config import RAG_PATH, settings
from services.resource_loader import extract_resource

logger = logging.getLogger(__name__)

INDEX_FILE = RAG_PATH / "index.json"
VECTORS_FILE = RAG_PATH / "vectors.bin"

SYSTEM_PROMPT = (
    "Tu es l'assistant de l'administration d'une plateforme de cours en ligne. Réponds en français, "
    "uniquement à partir des extraits de cours fournis. Après chaque information, cite l'extrait utilisé "
    "entre crochets, par exemple [1]. Si les extraits ne contiennent pas la réponse, dis-le clairement "
    "et n'invente rien."
)

STOPWORDS = set(
    "le la les un une des du de d l et ou en au aux a à ce ces cet cette que qui quoi dont où est sont "
    "pour par sur dans avec sans ne pas plus se sa son ses leur leurs il elle ils elles on nous vous je tu "
    "y comment quel quelle quels quelles the of and to in is are for".split()
)

_lock = threading.Lock()
_index: dict | None = None
_vectors: list[list[float]] | None = None


# ---------------------------------------------------------------- découpage

def chunk_text(text: str, size: int | None = None, overlap: int | None = None) -> list[str]:
    """Découper un texte en morceaux d'environ `size` caractères qui se chevauchent de `overlap`.

    La coupe se fait de préférence à un retour à la ligne, sinon à un espace, jamais au milieu d'un mot.
    """
    size = size or settings.RAG_CHUNK_CHARS
    overlap = overlap if overlap is not None else settings.RAG_CHUNK_OVERLAP
    text = re.sub(r"[ \t]+", " ", text or "")
    text = re.sub(r"\n{3,}", "\n\n", text).strip()
    if not text:
        return []
    chunks, start = [], 0
    while start < len(text):
        end = min(len(text), start + size)
        if end < len(text):
            cut = text.rfind("\n", start + size // 2, end)
            if cut == -1:
                cut = text.rfind(" ", start + size // 2, end)
            if cut > start:
                end = cut
        piece = text[start:end].strip()
        if piece:
            chunks.append(piece)
        if end >= len(text):
            break
        next_start = max(end - overlap, start + 1)
        # Le chevauchement commence au début d'un mot, jamais au milieu
        if not text[next_start - 1].isspace():
            space = re.search(r"\s", text[next_start:end])
            if space:
                next_start += space.end()
        start = next_start
    return chunks


# ---------------------------------------------------------------- recherche par mots-clés (BM25)

def tokenize(text: str) -> list[str]:
    """Mots en minuscules, sans accents ni mots vides (« le », « de »…)."""
    text = unicodedata.normalize("NFKD", text.lower())
    text = "".join(c for c in text if not unicodedata.combining(c))
    return [w for w in re.findall(r"[a-z0-9]+", text) if len(w) > 1 and w not in STOPWORDS]


def bm25_scores(query: str, docs: list[str], k1: float = 1.5, b: float = 0.75) -> list[float]:
    tokenized = [tokenize(d) for d in docs]
    n = len(tokenized)
    if not n:
        return []
    avgdl = sum(len(t) for t in tokenized) / n or 1.0
    df = Counter(w for t in tokenized for w in set(t))
    scores = []
    for tokens in tokenized:
        tf = Counter(tokens)
        score = 0.0
        for w in set(tokenize(query)):
            if w not in tf:
                continue
            idf = math.log(1 + (n - df[w] + 0.5) / (df[w] + 0.5))
            score += idf * tf[w] * (k1 + 1) / (tf[w] + k1 * (1 - b + b * len(tokens) / avgdl))
        scores.append(score)
    return scores


# ---------------------------------------------------------------- vecteurs

def _normalize(v: list[float]) -> list[float]:
    norm = math.sqrt(sum(x * x for x in v)) or 1.0
    return [x / norm for x in v]


def _cosine(a: list[float], b: list[float]) -> float:
    """Vecteurs déjà normalisés : la similarité cosinus est le produit scalaire."""
    return sum(x * y for x, y in zip(a, b, strict=True))


def _embeddings_enabled() -> bool:
    return bool(settings.AZURE_EMBEDDING_DEPLOYMENT)


# ---------------------------------------------------------------- index

def _save(index: dict, vectors: list[list[float]] | None) -> None:
    INDEX_FILE.write_text(json.dumps(index, ensure_ascii=False), encoding="utf-8")
    if vectors:
        data = array("f", [x for v in vectors for x in v])
        VECTORS_FILE.write_bytes(data.tobytes())
    elif VECTORS_FILE.exists():
        VECTORS_FILE.unlink()


def _load() -> tuple[dict | None, list[list[float]] | None]:
    global _index, _vectors
    if _index is not None:
        return _index, _vectors
    if not INDEX_FILE.is_file():
        return None, None
    index = json.loads(INDEX_FILE.read_text(encoding="utf-8"))
    vectors = None
    if index.get("mode") == "embeddings" and VECTORS_FILE.is_file():
        data = array("f")
        data.frombytes(VECTORS_FILE.read_bytes())
        dim = index["dimensions"]
        vectors = [list(data[i:i + dim]) for i in range(0, len(data), dim)]
    _index, _vectors = index, vectors
    return index, vectors


def reset_cache() -> None:
    """Oublier l'index gardé en mémoire (tests, ou nouvel index écrit sur disque)."""
    global _index, _vectors
    _index, _vectors = None, None


def build_index(lessons: list) -> dict:
    """Construire l'index à partir des leçons de tous les cours (envoyées par le backend).

    Chaque leçon : course_id, course_title, chapter_title, lesson_title, description, resources.
    Une ressource illisible (ou une vidéo jamais analysée) est ignorée et signalée, sans bloquer les autres.
    """
    chunks: list[dict] = []
    unreadable: list[str] = []
    for lesson in lessons:
        meta = {"course_id": lesson.course_id, "course": lesson.course_title,
                "chapter": lesson.chapter_title, "lesson": lesson.lesson_title}
        if lesson.description and lesson.description.strip():
            for piece in chunk_text(lesson.description):
                chunks.append({**meta, "resource": "Description de la leçon", "text": piece})
        for resource in lesson.resources:
            name = resource.title or resource.type
            try:
                text = extract_resource(resource, media_from_cache_only=True).strip()
            except Exception as e:
                logger.info("RAG : ressource ignorée '%s' (%s)", name, e)
                unreadable.append(f"{lesson.course_title} › {name}")
                continue
            for piece in chunk_text(text):
                chunks.append({**meta, "resource": name, "text": piece})

    mode, vectors, dimensions, warning = "bm25", None, 0, None
    if chunks and _embeddings_enabled():
        try:
            raw = get_llm_client().embed([c["text"] for c in chunks])
            vectors = [_normalize(v) for v in raw]
            mode, dimensions = "embeddings", len(vectors[0])
        except Exception as e:
            warning = f"Embeddings indisponibles ({e}) : recherche par mots-clés."
            logger.warning("RAG : %s", warning)

    index = {
        "mode": mode,
        "model": settings.AZURE_EMBEDDING_DEPLOYMENT if mode == "embeddings" else None,
        "dimensions": dimensions,
        "built_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "courses": len({c["course_id"] for c in chunks}),
        "chunks": chunks,
        "unreadable": unreadable,
    }
    with _lock:
        _save(index, vectors)
        reset_cache()
    return {**status(), "warning": warning}


def status() -> dict:
    index, _ = _load()
    if not index:
        return {"ready": False, "mode": None, "chunks": 0, "courses": 0, "built_at": None, "unreadable": [],
                "embedding_model": settings.AZURE_EMBEDDING_DEPLOYMENT or None}
    return {"ready": bool(index["chunks"]), "mode": index["mode"], "chunks": len(index["chunks"]),
            "courses": index.get("courses", 0), "built_at": index["built_at"], "unreadable": index["unreadable"],
            "embedding_model": index.get("model")}


def search(question: str, top_k: int | None = None) -> tuple[list[dict], str]:
    """Les morceaux les plus pertinents pour la question, avec leur score, et le mode de recherche utilisé."""
    top_k = top_k or settings.RAG_TOP_K
    index, vectors = _load()
    if not index or not index["chunks"]:
        raise LookupError("L'index est vide : cliquez sur « Mettre à jour l'index ».")
    chunks = index["chunks"]
    mode = index["mode"]
    scores = None
    if mode == "embeddings" and vectors:
        try:
            q = _normalize(get_llm_client().embed([question])[0])
            scores = [_cosine(q, v) for v in vectors]
        except Exception as e:
            logger.warning("RAG : embedding de la question impossible (%s), recherche par mots-clés", e)
            mode = "bm25"
    if scores is None:
        mode = "bm25"
        scores = bm25_scores(question, [c["text"] for c in chunks])
    ranked = sorted(range(len(chunks)), key=lambda i: scores[i], reverse=True)
    hits = [{**chunks[i], "score": round(scores[i], 4)} for i in ranked[:top_k] if scores[i] > 0]
    return hits, mode


def ask(question: str, top_k: int | None = None) -> dict:
    """Répondre à une question de l'administration à partir des passages retrouvés, avec les sources."""
    question = (question or "").strip()
    if len(question) < 3:
        raise ValueError("Posez une question d'au moins 3 caractères.")
    hits, mode = search(question, top_k)
    if not hits:
        return {"answer": "Aucun passage des cours ne correspond à cette question.", "sources": [], "mode": mode}
    context = "\n\n".join(
        f"[{i}] Cours « {h['course']} » › {h['chapter']} › {h['lesson']} › {h['resource']}\n{h['text']}"
        for i, h in enumerate(hits, 1)
    )
    prompt = f"Extraits des cours :\n\n{context}\n\nQuestion de l'administrateur : {question}"
    answer = get_llm_client().generate(prompt, system=SYSTEM_PROMPT, temperature=0.2)
    sources = [{"n": i, "course_id": h["course_id"], "course": h["course"], "chapter": h["chapter"],
                "lesson": h["lesson"], "resource": h["resource"], "score": h["score"],
                "excerpt": h["text"][:300]} for i, h in enumerate(hits, 1)]
    return {"answer": answer.strip(), "sources": sources, "mode": mode}
