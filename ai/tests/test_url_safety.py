"""Protection SSRF des liens de cours : adresses internes refusées, redirections vérifiées, vrais domaines YouTube/Google."""

import socket

import httpx
import pytest

from services import content_extractor as ce


def fake_dns(mapping):
    """Résolution DNS simulée : nom → adresse IP (aucun accès réseau pendant les tests)."""
    def resolve(host, port, *args, **kwargs):
        if host.replace(".", "").isdigit():  # adresse IP écrite directement : rendue telle quelle
            return [(socket.AF_INET, socket.SOCK_STREAM, 6, "", (host, port))]
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
        if request.headers["host"] == "cours.example.org":
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


def test_dns_rebinding_sans_effet(monkeypatch):
    """Le DNS répond d'abord une adresse publique puis 127.0.0.1 : la connexion part quand même vers
    l'adresse publique vérifiée, car le nom n'est résolu qu'une fois par requête."""
    reponses = iter(["93.184.215.14", "127.0.0.1", "127.0.0.1"])
    appels = []

    def resolve(host, port, *args, **kwargs):
        appels.append(host)
        return [(socket.AF_INET, socket.SOCK_STREAM, 6, "", (next(reponses), port))]
    monkeypatch.setattr(socket, "getaddrinfo", resolve)

    vus = []

    def handler(request):
        vus.append((request.url.host, request.headers["host"], request.extensions.get("sni_hostname")))
        return httpx.Response(200, text="contenu du cours")
    with httpx.Client(transport=httpx.MockTransport(handler)) as client:
        assert ce.fetch_public(client, "https://piege.example.org/cours").text == "contenu du cours"
    assert vus == [("93.184.215.14", "piege.example.org", "piege.example.org")]
    assert appels == ["piege.example.org"]


def test_redirection_resolue_puis_epinglee(dns):
    """Après une redirection, le nouveau nom est vérifié et la connexion part vers SON adresse vérifiée."""
    dns["autre.example.org"] = "93.184.215.99"

    def handler(request):
        if request.headers["host"] == "cours.example.org":
            return httpx.Response(302, headers={"location": "https://autre.example.org:8443/doc"})
        return httpx.Response(200, text=f"{request.url.host} {request.headers['host']}")
    with httpx.Client(transport=httpx.MockTransport(handler)) as client:
        assert ce.fetch_public(client, "https://cours.example.org/x").text == "93.184.215.99 autre.example.org:8443"


def test_adresse_ip_directe_sans_epinglage(dns):
    def handler(request):
        return httpx.Response(200, text=request.url.host)
    with httpx.Client(transport=httpx.MockTransport(handler)) as client:
        assert ce.fetch_public(client, "http://93.184.215.14/page").text == "93.184.215.14"


@pytest.mark.parametrize("url, attendu", [
    ("https://docs.google.com/document/d/ABC123/edit", "https://docs.google.com/document/d/ABC123/export?format=txt"),
    ("https://drive.google.com/file/d/XYZ/view", "https://drive.google.com/uc?export=download&id=XYZ"),
    ("https://pirate.com/docs.google.com/document/d/ABC123", "https://pirate.com/docs.google.com/document/d/ABC123"),
])
def test_liens_google(url, attendu):
    assert ce._google_export_url(url) == attendu
