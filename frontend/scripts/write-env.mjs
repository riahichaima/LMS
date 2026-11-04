// Écrit src/assets/env.js avant la compilation (Vercel, Netlify, Render « Static Site »…).
// Variable d'environnement de l'hébergeur : API_URL=https://api.mon-lms.com
import { writeFileSync } from 'node:fs';

const apiUrl = (process.env.API_URL || 'http://localhost:8090').replace(/\/+$/, '');

if (!/^https?:\/\/[^\s"'<>\\]+$/.test(apiUrl)) {
  console.error(`write-env : API_URL invalide : ${apiUrl}`);
  process.exit(1);
}

writeFileSync('src/assets/env.js', `window.__env = { apiUrl: ${JSON.stringify(apiUrl)} };\n`);
console.log(`write-env : API du LMS = ${apiUrl}`);
