"""Client du modèle déployé sur Azure AI Foundry."""

from functools import lru_cache

from config import settings


@lru_cache(maxsize=1)
def get_llm_client():
    """Client Azure OpenAI (quiz, TP, correction, assistant, vidéos)."""
    from agents.azure_openai_client import AzureOpenAIClient
    return AzureOpenAIClient()


def describe_provider() -> dict:
    """Informations affichées par /api/health (et dans le front)."""
    return {
        "provider": "azure",
        "chat_model": settings.AZURE_CHAT_DEPLOYMENT,
        "transcription_model": settings.AZURE_TRANSCRIBE_DEPLOYMENT,
        "video_vision": settings.VIDEO_FRAME_COUNT > 0,
    }
