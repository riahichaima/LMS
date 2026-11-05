"""Client HTTP pour les modèles déployés sur Azure AI Foundry (Azure OpenAI)."""

import logging
import time
from pathlib import Path

import httpx

from config import settings

logger = logging.getLogger(__name__)

# Erreurs passagères côté Azure (ex : "image_moderation_server_error") : on réessaie
RETRY_STATUS = {429, 500, 502, 503, 504}
MAX_ATTEMPTS = 5


class AzureOpenAIClient:
    """Chat (texte + images) et transcription audio via l'API REST Azure OpenAI."""

    provider = "azure"

    def __init__(self):
        if not settings.AZURE_OPENAI_ENDPOINT or not settings.AZURE_OPENAI_API_KEY:
            raise ValueError("AZURE_OPENAI_ENDPOINT et AZURE_OPENAI_API_KEY doivent être définis dans .env")
        self.endpoint = settings.AZURE_OPENAI_ENDPOINT.rstrip("/")
        self.model = settings.AZURE_CHAT_DEPLOYMENT
        self.headers = {"api-key": settings.AZURE_OPENAI_API_KEY}

    def _url(self, deployment: str, operation: str, api_version: str) -> str:
        return f"{self.endpoint}/openai/deployments/{deployment}/{operation}?api-version={api_version}"

    def _post(self, url: str, **kwargs) -> dict:
        for attempt in range(1, MAX_ATTEMPTS + 1):
            try:
                with httpx.Client(timeout=settings.AZURE_TIMEOUT) as client:
                    response = client.post(url, headers=self.headers, **kwargs)
            except httpx.ConnectError as e:
                raise ConnectionError(f"Impossible de joindre Azure OpenAI ({self.endpoint}).") from e
            except httpx.TimeoutException as e:
                raise TimeoutError("La requête Azure OpenAI a expiré.") from e
            if response.status_code in RETRY_STATUS and attempt < MAX_ATTEMPTS:
                # Débit Azure dépassé (429) : attendre le délai indiqué par Azure (60 s au plus)
                retry_after = response.headers.get("retry-after", "")
                delay = min(int(retry_after), 60) if retry_after.isdigit() else 2 * attempt
                logger.warning("Azure OpenAI %s, nouvel essai dans %d s (%d/%d)", response.status_code, delay, attempt, MAX_ATTEMPTS)
                time.sleep(delay)
                continue
            if response.status_code >= 400:
                raise RuntimeError(f"Azure OpenAI {response.status_code} : {response.text[:500]}")
            return response.json()

    def _chat(self, messages: list[dict], temperature: float, json_mode: bool, max_tokens: int | None = None) -> str:
        payload = {"messages": messages}
        if max_tokens:
            payload["max_tokens"] = max_tokens
        # Les modèles de raisonnement (gpt-5, o1, o3, o4...) n'acceptent que la température par défaut
        if not self.model.lower().startswith(("gpt-5", "o1", "o3", "o4")):
            payload["temperature"] = temperature
        if json_mode:
            payload["response_format"] = {"type": "json_object"}
        data = self._post(
            self._url(self.model, "chat/completions", settings.AZURE_OPENAI_API_VERSION), json=payload
        )
        return data["choices"][0]["message"]["content"] or ""

    def chat(self, messages: list[dict], temperature: float = 0.4) -> str:
        """Conversation multi-tours : messages [{role: system|user|assistant, content}]."""
        return self._chat(messages, temperature, json_mode=False)

    def generate(self, prompt: str, system: str = None, temperature: float = 0.7, json_mode: bool = False) -> str:
        messages = [{"role": "system", "content": system}] if system else []
        messages.append({"role": "user", "content": prompt})
        return self._chat(messages, temperature, json_mode)

    def describe_images(self, prompt: str, images_b64: list[str], detail: str = "low", max_tokens: int | None = None) -> str:
        """Envoyer des images JPEG (base64) au modèle avec une consigne (detail « high » pour lire du texte)."""
        content = [{"type": "text", "text": prompt}] + [
            {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{img}", "detail": detail}}
            for img in images_b64
        ]
        return self._chat([{"role": "user", "content": content}], temperature=0.2, json_mode=False, max_tokens=max_tokens)

    def transcribe(self, audio_path: str) -> str:
        """Transcrire un fichier audio (max 25 Mo) avec le déploiement de transcription."""
        path = Path(audio_path)
        # Contenu lu en mémoire : il peut être renvoyé tel quel si Azure demande un nouvel essai
        data = self._post(
            self._url(settings.AZURE_TRANSCRIBE_DEPLOYMENT, "audio/transcriptions", settings.AZURE_TRANSCRIBE_API_VERSION),
            files={"file": (path.name, path.read_bytes(), "audio/mpeg")},
            data={"response_format": "json"},
        )
        return data.get("text", "").strip()

    def is_available(self) -> bool:
        try:
            self.generate("ping", temperature=0)
            return True
        except Exception:
            return False
