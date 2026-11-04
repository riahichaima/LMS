import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

// À l'ouverture de l'application (première navigation), les pages de connexion / inscription
// laissent place à l'accueil : il reste la première page vue, même si l'onglet pointait sur /login.
export const landingFirstGuard: CanActivateFn = () => {
  const router = inject(Router);
  return router.navigated ? true : router.parseUrl('/');
};
