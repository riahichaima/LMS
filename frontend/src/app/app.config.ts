import { ApplicationConfig, inject, isDevMode, provideAppInitializer, provideZoneChangeDetection } from '@angular/core';
import { provideServiceWorker } from '@angular/service-worker';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import {
  provideRouter,
  withEnabledBlockingInitialNavigation,
  withHashLocation,
  withInMemoryScrolling,
  withNavigationErrorHandler,
  withRouterConfig,
  withViewTransitions
} from '@angular/router';
import { IconSetService } from '@coreui/icons-angular';
import { routes } from './app.routes';
import { authInterceptor } from './services/auth.interceptor';
import { reloadOnStaleChunk } from './services/stale-chunk';
import { AppUpdateService } from './services/app-update.service';

export const appConfig: ApplicationConfig = {
  providers: [
    // Rafraîchit la vue automatiquement après les réponses HTTP, timers, etc.
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideRouter(routes,
      withRouterConfig({
        onSameUrlNavigation: 'reload'
      }),
      withInMemoryScrolling({
        scrollPositionRestoration: 'top',
        anchorScrolling: 'enabled'
      }),
      withEnabledBlockingInitialNavigation(),
      withViewTransitions(),
      withHashLocation(),
      // Onglet resté ouvert pendant une mise en ligne : rechargement sur la nouvelle version
      withNavigationErrorHandler(reloadOnStaleChunk)
    ),
    IconSetService,
    provideAnimationsAsync(),
    // Application installable (PWA) : service worker en production seulement (jamais pendant ng serve)
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000'
    }),
    provideAppInitializer(() => inject(AppUpdateService).start())
  ]
};
