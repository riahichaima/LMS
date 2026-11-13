// Test de charge k6 : le LMS supporte-t-il plusieurs dizaines d'étudiants en même temps ?
//
//   k6 run -e API_URL=https://api.mon-lms.com -e VUS=30 deploy/k6/load-test.js
//   (option) -e APP_URL=https://app.mon-lms.com -e LMS_EMAIL=... -e LMS_PASSWORD=...
//
// Objectifs (seuils) : moins de 1 % d'erreurs et 95 % des réponses en moins de 800 ms.
import http from 'k6/http';
import { check, group, sleep } from 'k6';

const API = (__ENV.API_URL || 'http://localhost:8090').replace(/\/+$/, '');
const APP = (__ENV.APP_URL || '').replace(/\/+$/, '');
const VUS = Number(__ENV.VUS || 20);

export const options = {
  stages: [
    { duration: __ENV.RAMP || '30s', target: VUS }, // montée progressive
    { duration: __ENV.DURATION || '1m', target: VUS }, // charge stable
    { duration: '15s', target: 0 }, // descente
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<800'],
    checks: ['rate>0.99'],
    // Temps de chaque page affichés à part dans le résumé (mesure du cache avant / après)
    'http_req_duration{name:catalogue}': ['p(95)<800'],
    'http_req_duration{name:detail-cours}': ['p(95)<800'],
  },
};

// Connexion une seule fois (compte de test) : les utilisateurs virtuels parcourent ensuite le catalogue
export function setup() {
  if (!__ENV.LMS_EMAIL || !__ENV.LMS_PASSWORD) return { token: null };
  const res = http.post(`${API}/auth/login`, JSON.stringify({ email: __ENV.LMS_EMAIL, password: __ENV.LMS_PASSWORD }), {
    headers: { 'Content-Type': 'application/json' },
  });
  check(res, { 'connexion réussie': (r) => r.status === 200 });
  return { token: res.status === 200 ? res.json('token') : null };
}

export default function (data) {
  group('santé du backend', () => {
    check(http.get(`${API}/readyz`), { 'readyz répond 200': (r) => r.status === 200 });
  });

  if (APP) {
    group('page d\'accueil', () => {
      check(http.get(`${APP}/`), { 'frontend répond 200': (r) => r.status === 200 });
    });
  }

  if (data.token) {
    const headers = { Authorization: `Bearer ${data.token}` };
    const auth = { headers };
    group('catalogue', () => {
      const page = http.get(`${API}/api/courses?size=20`, { headers, tags: { name: 'catalogue' } });
      check(page, { 'catalogue 200': (r) => r.status === 200 });
      const first = page.status === 200 ? page.json('content.0.id') : null;
      if (first) {
        check(http.get(`${API}/api/courses/${first}`, { headers, tags: { name: 'detail-cours' } }),
          { 'détail du cours 200': (r) => r.status === 200 });
      }
      check(http.get(`${API}/api/feedback/ratings`, auth), { 'notes des cours 200': (r) => r.status === 200 });
    });
  }

  sleep(1);
}
