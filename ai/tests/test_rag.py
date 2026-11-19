"""RAG de l'administration : découpage, recherche (vecteurs ou mots-clés), réponse sourcée, sans appel à Azure."""

import math

import pytest
from fastapi.testclient import TestClient

from api.schemas import RagLesson, ResourceDetail
from config import settings
from services import rag

COURSES = {
    "java.pdf": "Spring Security protège les routes. Le jeton JWT est signé et vérifié à chaque requête. "
                "Le rôle de l'utilisateur est contrôlé avec PreAuthorize.",
    "web.pdf": "Angular affiche les pages. Les formulaires réactifs valident les champs avant l'envoi.",
    "math.pdf": "Une équation du second degré se résout avec le discriminant delta.",
}
WORDS = ["jwt", "jeton", "securite", "angular", "formulaire", "equation", "discriminant", "role"]


class FakeClient:
    """Modèle simulé : embeddings par mots-clés (une dimension par mot) et réponse prévisible."""

    def __init__(self):
        self.prompts = []
        self.embedded = 0

    def embed(self, texts):
        self.embedded += len(texts)
        vectors = []
        for t in texts:
            tokens = rag.tokenize(t)
            vectors.append([float(sum(1 for w in tokens if w.startswith(k))) + 0.01 for k in WORDS])
        return vectors

    def generate(self, prompt, system=None, temperature=0.7, json_mode=False):
        self.prompts.append((system, prompt))
        return "Le jeton JWT est vérifié à chaque requête [1]."


@pytest.fixture
def env(tmp_path, monkeypatch):
    """Index dans un dossier temporaire, ressources lues sans fichier réel, modèle simulé."""
    monkeypatch.setattr(rag, "INDEX_FILE", tmp_path / "index.json")
    monkeypatch.setattr(rag, "VECTORS_FILE", tmp_path / "vectors.bin")
    rag.reset_cache()

    def fake_extract(resource, media_from_cache_only=False):
        if resource.title == "video.mp4":
            if media_from_cache_only:
                raise ValueError("pas encore analysé")
            return "Vidéo : le rôle de l'utilisateur est vérifié par PreAuthorize."
        return COURSES[resource.title]

    monkeypatch.setattr(rag, "extract_resource", fake_extract)
    client = FakeClient()
    monkeypatch.setattr(rag, "get_llm_client", lambda: client)
    yield client
    rag.reset_cache()


def lessons():
    def lesson(cid, title, files):
        return RagLesson(course_id=cid, course_title=title, chapter_title="Chapitre 1", lesson_title="Leçon 1",
                         resources=[ResourceDetail(type="PDF", title=f, file_path=f"uploads/{f}") for f in files])
    return [lesson(1, "Java avancé", ["java.pdf", "video.mp4"]), lesson(2, "Web", ["web.pdf"]),
            lesson(3, "Mathématiques", ["math.pdf"])]


def test_chunks_overlap_and_never_cut_a_word():
    text = " ".join(f"mot{i}" for i in range(400))
    chunks = rag.chunk_text(text, size=200, overlap=50)
    assert len(chunks) > 5
    assert all(len(c) <= 200 for c in chunks)
    assert all(c.split()[0].startswith("mot") and c.split()[-1].startswith("mot") for c in chunks)
    # Chevauchement : le début d'un morceau répète la fin du précédent
    assert chunks[1].split()[0] in chunks[0].split()


def test_empty_text_gives_no_chunk():
    assert rag.chunk_text("   \n\n  ") == []


def test_bm25_ranks_the_relevant_passage_first():
    docs = list(COURSES.values())
    scores = rag.bm25_scores("Comment le jeton JWT est-il vérifié ?", docs)
    assert scores.index(max(scores)) == 0
    assert scores[2] == 0  # rien en commun avec les mathématiques


def test_keyword_index_when_no_embedding_model(env, monkeypatch):
    monkeypatch.setattr(settings, "AZURE_EMBEDDING_DEPLOYMENT", "")
    stats = rag.build_index(lessons())
    assert stats["ready"] and stats["mode"] == "bm25"
    assert stats["courses"] == 3 and stats["chunks"] == 3
    assert stats["unreadable"] == ["Java avancé › video.mp4"]  # vidéo jamais analysée : ignorée, signalée
    assert env.embedded == 0
    hits, mode = rag.search("formulaires réactifs Angular")
    assert mode == "bm25" and hits[0]["course"] == "Web"


def test_vector_index_and_cosine_search(env, monkeypatch):
    monkeypatch.setattr(settings, "AZURE_EMBEDDING_DEPLOYMENT", "text-embedding-3-small")
    stats = rag.build_index(lessons())
    assert stats["mode"] == "embeddings" and stats["embedding_model"] == "text-embedding-3-small"
    rag.reset_cache()  # relu depuis le disque, comme après un redémarrage
    hits, mode = rag.search("équation et discriminant", top_k=2)
    assert mode == "embeddings" and hits[0]["course"] == "Mathématiques"
    # Seuil : les passages sans rapport (Java, Web) ne sont pas retenus
    assert all(h["course"] == "Mathématiques" and h["score"] >= settings.RAG_MIN_SCORE for h in hits)
    assert math.isclose(sum(x * x for x in rag._load()[1][0]), 1.0, rel_tol=1e-4)  # vecteurs normalisés


def test_answer_cites_its_sources_and_uses_only_the_passages(env, monkeypatch):
    monkeypatch.setattr(settings, "AZURE_EMBEDDING_DEPLOYMENT", "")
    rag.build_index(lessons())
    result = rag.ask("Comment le jeton JWT est-il vérifié ?")
    assert "[1]" in result["answer"]
    first = result["sources"][0]
    assert first["n"] == 1 and first["course"] == "Java avancé" and first["resource"] == "java.pdf"
    system, prompt = env.prompts[-1]
    assert "uniquement à partir des extraits" in system and "n'invente rien" in system
    assert "[1] Cours « Java avancé »" in prompt


def test_question_without_index_or_too_short(env):
    with pytest.raises(LookupError):
        rag.search("JWT")
    with pytest.raises(ValueError):
        rag.ask("a")


def test_api_rejects_a_question_before_the_index_exists(env):
    from main import app
    response = TestClient(app).post("/api/rag/ask", json={"question": "Que dit le cours sur JWT ?"})
    assert response.status_code == 409
    assert "Mettre à jour l'index" in response.json()["detail"]


# ---------------------------------------------------------------- Qualité : seuil, pertinence, sources vérifiées


def test_normal_update_never_analyses_a_video(env):
    stats = rag.build_index(lessons())
    assert stats["unreadable"] == ["Java avancé › video.mp4"]


def test_full_indexing_analyses_the_videos_never_read(env):
    stats = rag.build_index(lessons(), analyze_media=True)
    assert stats["unreadable"] == []
    assert any(c["resource"] == "video.mp4" for c in rag._load()[0]["chunks"])


def test_unrelated_question_finds_no_passage(env, monkeypatch):
    monkeypatch.setattr(settings, "AZURE_EMBEDDING_DEPLOYMENT", "text-embedding-3-small")
    rag.build_index(lessons())
    real_embed = env.embed
    # Comme un vrai modèle : une question sans rapport a un vecteur éloigné de tous les passages
    monkeypatch.setattr(env, "embed", lambda texts: [[-1.0] * len(WORDS) if "chocolat" in t else v
                                                     for t, v in zip(texts, real_embed(texts), strict=True)])
    result = rag.ask("recette de cuisine au chocolat")
    assert result["sources"] == [] and "assez proche" in result["answer"]
    assert env.prompts == []  # aucun appel au modèle de réponse


def test_only_cited_passages_are_shown_as_sources(env, monkeypatch):
    monkeypatch.setattr(settings, "AZURE_EMBEDDING_DEPLOYMENT", "")
    monkeypatch.setattr(settings, "RAG_BM25_RATIO", 0.0)  # garder plusieurs passages pour le test
    rag.build_index(lessons())
    result = rag.ask("jeton JWT et formulaire Angular")
    assert [s["n"] for s in result["sources"]] == [1]


def test_answer_without_citation_is_refused(env, monkeypatch):
    monkeypatch.setattr(settings, "AZURE_EMBEDDING_DEPLOYMENT", "")
    rag.build_index(lessons())
    monkeypatch.setattr(env, "generate", lambda *a, **k: "Les branches infinies sont liées aux limites.")
    result = rag.ask("Comment le jeton JWT est-il vérifié ?")
    assert result["sources"] == [] and "ne répondent pas" in result["answer"]


def test_model_saying_no_answer_gives_no_source(env, monkeypatch):
    monkeypatch.setattr(settings, "AZURE_EMBEDDING_DEPLOYMENT", "")
    rag.build_index(lessons())
    monkeypatch.setattr(env, "generate", lambda *a, **k: rag.NO_ANSWER)
    result = rag.ask("Comment le jeton JWT est-il vérifié ?")
    assert result["sources"] == []
