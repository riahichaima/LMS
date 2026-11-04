"""Analyse des vidéos (fichier local ou YouTube) : transcription du son + lecture des images.

- Son : transcrit par le déploiement Azure AZURE_TRANSCRIBE_DEPLOYMENT.
- Images : quelques images réparties sur la vidéo sont décrites par le modèle de chat
  (slides, code, schémas affichés).
"""

import base64
import logging
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

import imageio_ffmpeg

from agents.llm_client import get_llm_client
from config import TMP_PATH, settings
from prompts.video_prompts import FRAMES_DESCRIPTION_PROMPT

logger = logging.getLogger(__name__)

FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()
AUDIO_CHUNK_SECONDS = 1200  # 20 min de mp3 mono 32 kb/s ≈ 5 Mo (< 25 Mo, limite Azure)


def _run(cmd: list[str], tool: str) -> subprocess.CompletedProcess:
    result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", check=False)
    if result.returncode != 0:
        raise RuntimeError(f"{tool} a échoué : {result.stderr[-500:]}")
    return result


def _duration_seconds(video_path: str) -> float:
    """Durée de la vidéo, lue dans la sortie de ffmpeg (pas besoin de ffprobe)."""
    result = subprocess.run([FFMPEG, "-i", video_path], capture_output=True, text=True, encoding="utf-8", errors="replace")
    match = re.search(r"Duration: (\d+):(\d+):(\d+\.?\d*)", result.stderr)
    if not match:
        return 0.0
    h, m, s = match.groups()
    return int(h) * 3600 + int(m) * 60 + float(s)


# ── Son ───────────────────────────────────────────────────────────

def _transcribe_audio(video_path: str, workdir: Path) -> str:
    """Extraire le son de la vidéo puis le transcrire sur Azure.

    mp3 mono compressé, découpé en morceaux de 20 min pour respecter la limite de 25 Mo.
    """
    _run([FFMPEG, "-i", video_path, "-vn", "-ac", "1", "-ar", "16000", "-b:a", "32k",
          "-f", "segment", "-segment_time", str(AUDIO_CHUNK_SECONDS), "-y",
          str(workdir / "audio_%03d.mp3")], "ffmpeg")
    client = get_llm_client()
    return "\n".join(client.transcribe(str(chunk)) for chunk in sorted(workdir.glob("audio_*.mp3")))


# ── Images ────────────────────────────────────────────────────────

def _describe_frames(video_path: str, workdir: Path) -> str:
    """Extraire quelques images réparties sur la vidéo et les faire décrire par le modèle."""
    client = get_llm_client()
    count = settings.VIDEO_FRAME_COUNT
    if count <= 0:
        return ""

    duration = _duration_seconds(video_path)
    if duration <= 0:
        return ""

    images = []
    for i in range(count):
        frame = workdir / f"frame_{i:02d}.jpg"
        timestamp = duration * (i + 0.5) / count
        try:
            _run([FFMPEG, "-ss", f"{timestamp:.1f}", "-i", video_path, "-frames:v", "1",
                  "-vf", "scale=768:-2", "-q:v", "4", "-y", str(frame)], "ffmpeg")
            images.append(base64.b64encode(frame.read_bytes()).decode())
        except Exception as e:
            logger.warning("Image %d non extraite : %s", i, e)

    if not images:
        return ""
    return client.describe_images(FRAMES_DESCRIPTION_PROMPT.format(count=len(images)), images)


# ── Points d'entrée ───────────────────────────────────────────────

def analyze_video(video_path: str) -> str:
    """Retourne le contenu pédagogique d'une vidéo : transcription + contenu visuel."""
    workdir = Path(tempfile.mkdtemp(dir=TMP_PATH))
    try:
        transcript = _transcribe_audio(video_path, workdir)
        try:
            visual = _describe_frames(video_path, workdir)
        except Exception as e:
            logger.warning("Analyse des images ignorée : %s", e)
            visual = ""
    finally:
        shutil.rmtree(workdir, ignore_errors=True)

    parts = []
    if transcript:
        parts.append(f"Transcription de la vidéo :\n{transcript}")
    if visual:
        parts.append(f"Contenu affiché à l'écran :\n{visual}")
    if not parts:
        raise ValueError("Aucun contenu exploitable dans la vidéo (pas de son ni d'image lisible).")
    return "\n\n".join(parts)


def analyze_audio(audio_path: str) -> str:
    """Retourne la transcription d'un fichier audio (mp3, wav, m4a...)."""
    workdir = Path(tempfile.mkdtemp(dir=TMP_PATH))
    try:
        transcript = _transcribe_audio(audio_path, workdir)
    finally:
        shutil.rmtree(workdir, ignore_errors=True)
    if not transcript:
        raise ValueError("Aucune parole détectée dans le fichier audio.")
    return f"Transcription de l'audio :\n{transcript}"


def analyze_youtube(url: str) -> str:
    """Télécharger une vidéo YouTube (480p max) puis l'analyser."""
    workdir = Path(tempfile.mkdtemp(dir=TMP_PATH))
    try:
        _run([sys.executable, "-m", "yt_dlp", "-f", "best[height<=480]/worst", "--ffmpeg-location", FFMPEG,
              "--no-playlist", "-o", str(workdir / "video.%(ext)s"), url], "yt-dlp")
        video = next(workdir.glob("video.*"), None)
        if video is None:
            raise FileNotFoundError("Vidéo YouTube téléchargée introuvable.")
        return analyze_video(str(video))
    finally:
        shutil.rmtree(workdir, ignore_errors=True)
