import { NavigationError } from '@angular/router';

/**
 * Onglet ouvert avant une nouvelle mise en ligne : les fichiers des pages ont changé de nom,
 * l'ancien fichier demandé n'existe plus et la navigation échoue sans rien afficher.
 * On recharge alors le site sur la page demandée (nouvelle version), une seule fois par minute
 * pour ne jamais boucler si le problème venait d'ailleurs (réseau coupé…).
 */
const STALE_CHUNK = /Failed to fetch dynamically imported module|error loading dynamically imported module|Importing a module script failed|Expected a JavaScript.*MIME type|ChunkLoadError|Loading chunk .* failed/i;
const RELOAD_KEY = 'lms_stale_reload_at';

export function isStaleChunkError(error: unknown): boolean {
  const message = error instanceof Error ? `${error.name} ${error.message}` : String(error ?? '');
  return STALE_CHUNK.test(message);
}

export function reloadOnStaleChunk(event: NavigationError): void {
  if (!isStaleChunkError(event.error)) return;
  let last = 0;
  try { last = Number(sessionStorage.getItem(RELOAD_KEY)) || 0; } catch { /* stockage indisponible */ }
  if (Date.now() - last < 60_000) return;
  try { sessionStorage.setItem(RELOAD_KEY, String(Date.now())); } catch { /* stockage indisponible */ }
  // Adresse de la page demandée sans relancer le routeur, puis rechargement complet
  history.replaceState(history.state, '', `${location.pathname}${location.search}#${event.url}`);
  location.reload();
}
