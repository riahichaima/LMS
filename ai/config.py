"""Configuration centralisée du service LMS AI (chargée depuis .env)."""

from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

BASE_DIR = Path(__file__).parent


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=BASE_DIR / ".env", env_file_encoding="utf-8", extra="ignore")

    # Azure AI Foundry / Azure OpenAI
    AZURE_OPENAI_ENDPOINT: str = ""  # ex: https://<ressource>.openai.azure.com
    AZURE_OPENAI_API_KEY: str = ""
    AZURE_OPENAI_API_VERSION: str = "2024-10-21"
    AZURE_CHAT_DEPLOYMENT: str = "gpt-4.1-mini"  # quiz, TP, correction, chat + images de la vidéo
    AZURE_TRANSCRIBE_DEPLOYMENT: str = "gpt-4o-mini-transcribe"  # son des vidéos
    AZURE_TRANSCRIBE_API_VERSION: str = "2025-03-01-preview"
    AZURE_TIMEOUT: float = 300.0

    # Vidéo : nombre d'images extraites et analysées par le modèle (0 = désactivé)
    VIDEO_FRAME_COUNT: int = 8

    # Dossier de travail (fichiers temporaires + contenus de l'assistant)
    UPLOAD_DIR: str = "uploads"

    # Dossier de travail du backend Spring Boot : les `file_path` envoyés par le
    # backend (ex: "uploads/videos/<uuid>.pdf") sont relatifs à ce dossier.
    BACKEND_ROOT_DIR: str = str(BASE_DIR.parent / "lms")

    # Taille max du contenu du cours envoyé au modèle
    MAX_CONTENT_CHARS: int = 60000

    @property
    def max_content_chars(self) -> int:
        return self.MAX_CONTENT_CHARS


settings = Settings()

UPLOAD_PATH = BASE_DIR / settings.UPLOAD_DIR
TMP_PATH = UPLOAD_PATH / "tmp"
CONTENT_STORE_PATH = UPLOAD_PATH / "contents"
# Texte déjà extrait des vidéos (une vidéo n'est analysée qu'une fois)
EXTRACT_CACHE_PATH = UPLOAD_PATH / "cache"
for _p in (TMP_PATH, CONTENT_STORE_PATH, EXTRACT_CACHE_PATH):
    _p.mkdir(parents=True, exist_ok=True)
