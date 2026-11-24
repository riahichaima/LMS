"""Extraction de texte depuis un PDF, une page web ou un lien Google Drive/Docs."""

import io
import ipaddress
import re
import socket
from urllib.parse import urljoin, urlparse

import httpx
from bs4 import BeautifulSoup
from pypdf import PdfReader

_HEADERS = {"User-Agent": "Mozilla/5.0 (compatible; LMS_AI/1.0)"}


YOUTUBE_HOSTS = ("youtube.com", "youtu.be")
MAX_REDIRECTS = 5


def _host_matches(url: str, domains: tuple[str, ...]) -> bool:
    """Vrai si le nom de domaine du lien est l'un des domaines (ou un sous-domaine), pas une simple sous-chaîne."""
    host = (urlparse(url).hostname or "").lower().rstrip(".")
    return any(host == d or host.endswith("." + d) for d in domains)


def is_youtube_url(url: str) -> bool:
    return urlparse(url).scheme in ("http", "https") and _host_matches(url, YOUTUBE_HOSTS)


def resolve_public_ip(url: str) -> str:
    """Protection SSRF : renvoie l'adresse IP publique à laquelle se connecter pour ce lien.

    Refuse les liens non http(s), localhost, les réseaux privés (conteneurs Docker, réseau de la VM),
    les adresses link-local (dont 169.254.169.254, service de métadonnées Azure) et les adresses réservées.
    Le nom de domaine est résolu UNE seule fois : la connexion se fait ensuite à cette adresse vérifiée
    (voir fetch_public), donc un DNS qui change de réponse entre-temps (« DNS rebinding ») ne sert à rien.
    """
    parsed = urlparse(url)
    if parsed.scheme not in ("http", "https") or not parsed.hostname:
        raise ValueError("Lien refusé : seules les adresses http(s) sont acceptées.")
    try:
        infos = socket.getaddrinfo(parsed.hostname, parsed.port or (443 if parsed.scheme == "https" else 80))
    except socket.gaierror as e:
        raise ValueError(f"Lien refusé : nom de domaine introuvable ({parsed.hostname}).") from e
    ips = [ipaddress.ip_address(info[4][0]) for info in infos]
    if not ips:
        raise ValueError(f"Lien refusé : nom de domaine introuvable ({parsed.hostname}).")
    for ip in ips:
        if not ip.is_global or ip.is_multicast:
            raise ValueError("Lien refusé : adresse interne ou réservée.")
    return str(ips[0])


def check_public_url(url: str) -> None:
    """Vérifie seulement que le lien mène à une adresse publique (voir resolve_public_ip)."""
    resolve_public_ip(url)


def extract_from_pdf(source) -> str:
    """Extraire le texte d'un PDF (chemin de fichier ou flux binaire)."""
    reader = PdfReader(source)
    text = "\n\n".join(t for t in (page.extract_text() for page in reader.pages) if t)
    if not text.strip():
        raise ValueError("Le PDF ne contient pas de texte extractible (PDF scanné ?).")
    return text


def extract_from_url(url: str) -> str:
    """Extraire le texte d'un lien externe (page web, PDF en ligne, Google Docs/Drive)."""
    url = _google_export_url(url)
    # Pas de connexion réutilisée : chaque requête ouvre une connexion neuve vers l'adresse vérifiée
    limits = httpx.Limits(max_keepalive_connections=0)
    with httpx.Client(timeout=30.0, follow_redirects=False, headers=_HEADERS, limits=limits) as client:
        response = fetch_public(client, url)
        url = str(response.url)  # même chemin ; l'hôte peut être l'adresse IP vérifiée

    content_type = response.headers.get("content-type", "")
    if "pdf" in content_type or url.lower().endswith(".pdf"):
        return extract_from_pdf(io.BytesIO(response.content))
    if "html" not in content_type:
        return response.text

    soup = BeautifulSoup(response.text, "lxml")
    for element in soup(["script", "style", "nav", "footer", "header", "aside"]):
        element.decompose()
    lines = (line.strip() for line in soup.get_text(separator="\n").splitlines())
    return "\n".join(line for line in lines if line)


def _google_export_url(url: str) -> str:
    """Convertir un lien Google Docs/Drive en lien de téléchargement direct."""
    match = re.search(r"/d/([a-zA-Z0-9_-]+)", url) or re.search(r"id=([a-zA-Z0-9_-]+)", url)
    if not match:
        return url
    path = urlparse(url).path
    if _host_matches(url, ("docs.google.com",)) and path.startswith("/document"):
        return f"https://docs.google.com/document/d/{match.group(1)}/export?format=txt"
    if _host_matches(url, ("drive.google.com",)):
        return f"https://drive.google.com/uc?export=download&id={match.group(1)}"
    return url


def fetch_public(client: httpx.Client, url: str) -> httpx.Response:
    """GET en suivant les redirections à la main : chaque adresse (y compris après redirection) est vérifiée.

    La connexion part vers l'adresse IP vérifiée (et pas vers le nom, qui serait résolu une 2e fois) ;
    le nom reste dans l'en-tête Host et dans le SNI, donc le certificat HTTPS est contrôlé pour ce nom.
    """
    for _ in range(MAX_REDIRECTS + 1):
        response = client.send(pinned_request(client, url))
        if response.is_redirect and response.headers.get("location"):
            url = urljoin(url, response.headers["location"])
            continue
        response.raise_for_status()
        return response
    raise ValueError("Lien refusé : trop de redirections.")


def pinned_request(client: httpx.Client, url: str) -> httpx.Request:
    """Requête GET vers l'adresse IP vérifiée du lien, avec le vrai nom en Host et en SNI."""
    ip = resolve_public_ip(url)
    target = httpx.URL(url)
    host = target.host
    headers = {}
    extensions = {}
    if not _is_ip_literal(host):
        headers["Host"] = host if target.port is None else f"{host}:{target.port}"
        extensions["sni_hostname"] = host
        target = target.copy_with(host=ip)
    return client.build_request("GET", target, headers=headers, extensions=extensions)


def _is_ip_literal(host: str) -> bool:
    try:
        ipaddress.ip_address(host)
        return True
    except ValueError:
        return False
