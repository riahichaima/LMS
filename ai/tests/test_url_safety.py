"""Protection SSRF des liens de cours : adresses internes refusées, redirections vérifiées, vrais domaines YouTube/Google."""

import socket

import httpx
import pytest

from services import content_extractor as ce


def fake_dns(mapping):
    """Résolution DNS simulée : nom → adresse IP (aucun accès réseau pendant les tests)."""
    def resolve(host, port, *args, **kwargs):
        if host not in mapping:
            raise socket.gaierror("inconnu")
        return [(socket.AF_INET, socket.SOCK_STREAM, 6, "", (mapping[host], port))]
    return resolve


@pytest.fixture
def dns(monkeypatch):
    mapping = {
        "cours.example.org": "93.184.215.14",
        "localhost": "127.0.0.1",
        "backend": "172.18.0.4",           # conteneur Docker
        "intranet.example.org": "10.0.0.7",
        "metadata.example.org": "169.254.169.254",  # service de métadonnées Azure
    }
    monkeypatch.setattr(socket, "getaddrinfo", fake_dns(mapping))
    return mapping


@pytest.mark.parametrize("url", [
    "https://www.youtube.com/watch?v=abc",
    "https://youtube.com/watch?v=abc",
    "https://m.youtube.com/watch?v=abc",
    "https://youtu.be/abc",
])
def test_vrais_liens_youtube(url):
    assert ce.is_youtube_url(url)


@pytest.mark.parametrize("url", [
    "http://site-pirate.com/?youtube.com",
    "https://youtube.com.site-pirate.com/watch",
    "https://notyoutube.com/watch",
    "file:///etc/passwd",
    "-o/tmp/x",
])
def test_faux_liens_youtube(url):
    assert not ce.is_youtube_url(url)


def test_lien_public_accepte(dns):
    ce.check_public_url("https://cours.example.org/chapitre1")


@pytest.mark.parametrize("url", [
    "http://localhost:8090/api/admin",
    "http://127.0.0.1/",
    "http://backend:8091/actuator/env",
    "http://intranet.example.org/",
    "http://169.254.169.254/metadata/instance",
    "http://metadata.example.org/",
    "http://[::1]/",
    "file:///etc/passwd",
    "ftp://cours.example.org/fichier",
    "http://inconnu.invalid/",
])
def test_adresses_internes_refusees(dns, url):
    with pytest.raises(ValueError):
        ce.check_public_url(url)


def test_redirection_vers_adresse_interne_refusee(dns):
    def handler(request):
        if request.url.host == "cours.example.org":
            return httpx.Response(302, headers={"location": "http://backend:8091/actuator/env"})
        return httpx.Response(200, text="secret interne")
    with httpx.Client(transport=httpx.MockTransport(handler)) as client:
        with pytest.raises(ValueError):
            ce.fetch_public(client, "https://cours.example.org/lien")


def test_redirection_publique_suivie(dns):
    def handler(request):
        if request.url.path == "/ancien":
            return httpx.Response(301, headers={"location": "/nouveau"})
        return httpx.Response(200, text="contenu du cours")
    with httpx.Client(transport=httpx.MockTransport(handler)) as client:
        response = ce.fetch_public(client, "https://cours.example.org/ancien")
    assert response.text == "contenu du cours"


def test_trop_de_redirections(dns):
    with httpx.Client(transport=httpx.MockTransport(lambda r: httpx.Response(302, headers={"location": "/boucle"}))) as client:
        with pytest.raises(ValueError):
            ce.fetch_public(client, "https://cours.example.org/boucle")


@pytest.mark.parametrize("url, attendu", [
    ("https://docs.google.com/document/d/ABC123/edit", "https://docs.google.com/document/d/ABC123/export?format=txt"),
    ("https://drive.google.com/file/d/XYZ/view", "https://drive.google.com/uc?export=download&id=XYZ"),
    ("https://pirate.com/docs.google.com/document/d/ABC123", "https://pirate.com/docs.google.com/document/d/ABC123"),
])
def test_liens_google(url, attendu):
    assert ce._google_export_url(url) == attendu
