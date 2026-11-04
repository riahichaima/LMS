# LMS_AI — Service IA du LMS

Service FastAPI appelé par le backend Spring Boot (`lms`). Le modèle est déployé sur **Azure AI Foundry** :
`gpt-4.1-mini` (quiz, TP, correction, assistant, images des vidéos) et `gpt-4o-mini-transcribe` (son des vidéos).

**Flux principal :** le prof ajoute des ressources (PDF, vidéo, YouTube, lien) → le backend appelle
`/api/orchestrator/generate-assessments` → LMS_AI extrait le contenu (texte PDF, son + images
de la vidéo, texte de la page web) → le modèle génère un quiz par niveau avec la bonne réponse et l'explication
→ le backend enregistre et renvoie au prof.

## Structure

```
main.py                         # application FastAPI + /api/health
config.py                       # paramètres (.env)
api/
  schemas.py                    # format des requêtes du backend
  assessment_routes.py          # POST /api/orchestrator/generate-assessments
  correction_routes.py          # POST /api/correct/  et  /api/correct/correct-quiz
  progress_routes.py            # POST /api/progress/analyze
  content_routes.py             # assistant : POST /api/content/upload/pdf | upload/video | ingest/url
  chat_routes.py                # assistant : POST /api/chat/ask
services/
  resource_loader.py            # ressource backend -> texte (PDF, vidéo, YouTube, lien)
  content_extractor.py          # extraction PDF / web / Google Docs-Drive
  video_processor.py            # vidéo : son (transcription) + images (gpt-4.1-mini)
  content_store.py              # contenus indexés par l'assistant (JSON)
  assessment_service.py         # génération quiz/TP par niveau + format Spring Boot
agents/
  llm_client.py                 # client du modèle Azure
  azure_openai_client.py, base_agent.py
  tutor_agent.py                # réponses de l'assistant
  quiz_agent.py                 # QCM + TP (avec correction)
  correction_agent.py           # correction TP (LLM) + quiz (comparaison)
  recommendation_agent.py       # recommandations de progression
prompts/                        # prompts des agents
```

## Installation

```powershell
python -m venv venv
.\venv\Scripts\activate
pip install -r requirements.txt
```

`ffmpeg` est fourni par le package `imageio-ffmpeg` (rien à installer).

## Azure AI Foundry

Une vidéo est analysée en deux parties : le **son** est transcrit par `gpt-4o-mini-transcribe`, et 8 **images**
réparties sur la vidéo (slides, code, schémas) sont lues par `gpt-4.1-mini`. Les deux textes servent ensuite à
générer le quiz et le TP selon le niveau choisi par le professeur.

1. Dans le projet Foundry : **Models + endpoints → Deploy model → Deploy base model**.
2. Déployer `gpt-4.1-mini` (nom de déploiement : `gpt-4.1-mini`) puis `gpt-4o-mini-transcribe`
   (nom : `gpt-4o-mini-transcribe`). Si un autre nom de déploiement est utilisé, le reporter dans `.env`.
3. Ouvrir le déploiement `gpt-4.1-mini` : copier l'**Endpoint** (`https://<ressource>.openai.azure.com`) et la **Key**.
4. Dans `.env` : `AZURE_OPENAI_ENDPOINT=...`, `AZURE_OPENAI_API_KEY=...`.
5. Redémarrer LMS_AI et vérifier `http://localhost:8000/api/health` (`"model_available": true`).

## Configuration (.env)

| Variable | Défaut | Rôle |
|---|---|---|
| `AZURE_OPENAI_ENDPOINT` / `AZURE_OPENAI_API_KEY` | — | endpoint et clé Azure |
| `AZURE_CHAT_DEPLOYMENT` | `gpt-4.1-mini` | déploiement chat + images |
| `AZURE_TRANSCRIBE_DEPLOYMENT` | `gpt-4o-mini-transcribe` | déploiement transcription |
| `VIDEO_FRAME_COUNT` | `8` | images analysées par vidéo (0 = désactivé) |
| `BACKEND_ROOT_DIR` | `../lms` | dossier du backend : les `file_path` envoyés (ex. `uploads/videos/x.pdf`) sont relatifs à ce dossier |
| `MAX_CONTENT_CHARS` | `60000` | taille max du contenu envoyé au modèle |

## Lancement

```powershell
uvicorn main:app --port 8000 --reload
```

Documentation interactive : `http://127.0.0.1:8000/docs`
