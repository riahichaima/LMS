// L'adresse de l'API vient de assets/env.js (généré au déploiement) : la même compilation
// fonctionne sur n'importe quel serveur (Docker, Vercel, Render, Azure…).
const runtime = (globalThis as { __env?: { apiUrl?: string } }).__env;

export const environment = {
  production: true,
  apiUrl: (runtime?.apiUrl || 'http://localhost:8090').replace(/\/+$/, '')
};
