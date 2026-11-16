import { inject, Injectable } from '@angular/core';
import { NavigationStart, Router } from '@angular/router';
import { SwUpdate } from '@angular/service-worker';
import { filter } from 'rxjs/operators';

/**
 * Application installable (PWA) : quand une nouvelle version est mise en ligne, le service worker
 * la télécharge en arrière-plan. On l'applique au prochain changement de page, jamais au milieu
 * d'une page (un quiz en cours n'est pas interrompu). Si le cache devient incohérent, on recharge.
 */
@Injectable({ providedIn: 'root' })
export class AppUpdateService {
  private readonly swUpdate = inject(SwUpdate);
  private readonly router = inject(Router);
  private updateReady = false;

  start(): void {
    if (!this.swUpdate.isEnabled) return;
    this.swUpdate.versionUpdates
      .pipe(filter((event) => event.type === 'VERSION_READY'))
      .subscribe(() => (this.updateReady = true));
    this.swUpdate.unrecoverable.subscribe(() => this.reload());
    this.router.events
      .pipe(filter((event): event is NavigationStart => event instanceof NavigationStart))
      .subscribe((event) => {
        if (!this.updateReady) return;
        this.updateReady = false;
        // Nouvelle version sur la page demandée
        history.replaceState(history.state, '', `${location.pathname}${location.search}#${event.url}`);
        this.swUpdate.activateUpdate().finally(() => this.reload());
      });
  }

  /** Isolé pour les tests. */
  protected reload(): void {
    location.reload();
  }
}
