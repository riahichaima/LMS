"""Stockage des contenus indexés par l'assistant IA (un fichier JSON par contenu)."""

import json
import threading
from datetime import datetime

from config import CONTENT_STORE_PATH

_lock = threading.Lock()


def save_content(title: str, content_type: str, text: str, source: str = "") -> dict:
    with _lock:
        ids = [int(p.stem) for p in CONTENT_STORE_PATH.glob("*.json") if p.stem.isdigit()]
        content = {
            "id": max(ids, default=0) + 1,
            "title": title,
            "content_type": content_type,
            "source": source,
            "text": text,
            "created_at": datetime.now().isoformat(timespec="seconds"),
        }
        (CONTENT_STORE_PATH / f"{content['id']}.json").write_text(
            json.dumps(content, ensure_ascii=False), encoding="utf-8"
        )
    return content


def get_content(content_id: int) -> dict | None:
    path = CONTENT_STORE_PATH / f"{content_id}.json"
    return json.loads(path.read_text(encoding="utf-8")) if path.is_file() else None


def get_latest_content(video: bool | None = None) -> dict | None:
    """Dernier contenu indexé (optionnellement : seulement vidéo / seulement texte)."""
    ids = sorted((int(p.stem) for p in CONTENT_STORE_PATH.glob("*.json") if p.stem.isdigit()), reverse=True)
    for content_id in ids:
        content = get_content(content_id)
        if video is None or (content["content_type"] in ("video", "youtube")) == video:
            return content
    return None
