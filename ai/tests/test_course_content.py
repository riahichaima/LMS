"""Contenu du cours pour les quiz / TP : fichiers du backend retrouvés, jamais de quiz inventé sans contenu."""

import pytest

from api.schemas import ResourceDetail
from config import settings
from services import resource_loader
from services.assessment_service import build_assessment_content
from services.resource_loader import resolve_backend_path


@pytest.fixture
def backend_root(tmp_path, monkeypatch):
    """Dossier du backend tel que le voit le service IA (Docker : /backend)."""
    (tmp_path / "uploads" / "videos").mkdir(parents=True)
    (tmp_path / "uploads" / "videos" / "cours.pdf").write_bytes(b"%PDF")
    monkeypatch.setattr(settings, "BACKEND_ROOT_DIR", str(tmp_path))
    return tmp_path


def test_container_path_of_backend_is_translated(backend_root):
    # Chemin enregistré par le backend dans son propre conteneur
    found = resolve_backend_path("/app/uploads/videos/cours.pdf")
    assert found == (backend_root / "uploads" / "videos" / "cours.pdf").resolve()


def test_windows_path_is_translated(backend_root):
    found = resolve_backend_path(r"C:\Users\x\Desktop\LMS-PFE\backend\uploads\videos\cours.pdf")
    assert found.name == "cours.pdf"


def test_relative_path_still_works(backend_root):
    assert resolve_backend_path("uploads/videos/cours.pdf").is_file()


def test_path_cannot_leave_uploads(backend_root):
    (backend_root / "secret.txt").write_text("x")
    with pytest.raises(FileNotFoundError):
        resolve_backend_path("/app/uploads/../secret.txt")
    with pytest.raises(FileNotFoundError):
        resolve_backend_path("/app/uploads/videos/absent.pdf")


def test_unreadable_course_stops_generation(backend_root):
    resources = [ResourceDetail(type="PDF", title="Chapitre 1", file_path="/app/uploads/videos/absent.pdf")]
    with pytest.raises(ValueError, match="n'a pas pu être lu"):
        build_assessment_content(["Leçon: Leçon 1"], resources)


def test_chapter_without_resource_needs_real_description():
    with pytest.raises(ValueError, match="ne contient pas encore de ressource"):
        build_assessment_content(["Leçon: Leçon 1"], [])
    long_description = "Leçon: Réseaux. " + "Le modèle OSI comporte sept couches. " * 10
    assert "OSI" in build_assessment_content([long_description], [])


def test_readable_resource_is_used(backend_root, monkeypatch):
    monkeypatch.setattr(resource_loader, "extract_from_pdf", lambda path: "Le protocole TCP garantit la livraison.")
    resources = [ResourceDetail(type="PDF", title="Cours TCP", file_path="/app/uploads/videos/cours.pdf")]
    content = build_assessment_content(["Leçon: Leçon 1"], resources)
    assert "protocole TCP" in content and "Cours TCP" in content
