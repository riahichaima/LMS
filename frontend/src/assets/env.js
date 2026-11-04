// Configuration d'exécution du frontend (chargée avant l'application).
// En développement : valeurs ci-dessous. En production, ce fichier est régénéré :
//  - image Docker : au démarrage du conteneur, à partir de la variable API_URL ;
//  - Vercel : au moment du build (scripts/write-env.mjs).
window.__env = window.__env || { apiUrl: 'http://localhost:8090' };
