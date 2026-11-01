import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const roleGuard = (allowedRoles: string[]): CanActivateFn => {
  return (route, state) => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (!authService.isLoggedIn()) {
      router.navigate(['/']);
      return false;
    }

    const currentRole = authService.userRole().replace('ROLE_', '');
    // Le super administrateur accède aussi à toutes les pages administrateur
    const isAllowed = allowedRoles.some(role => {
      const allowed = role.replace('ROLE_', '');
      return allowed === currentRole || (allowed === 'ADMIN' && currentRole === 'SUPER_ADMIN');
    });

    if (isAllowed) {
      return true;
    }

    // Redirect to dashboard if unauthorized role
    router.navigate(['/dashboard']);
    return false;
  };
};
