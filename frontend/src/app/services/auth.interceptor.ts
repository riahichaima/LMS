import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { Injector, inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const injector = inject(Injector);
  const token = localStorage.getItem('lms_token');
  const authReq = token
    ? req.clone({ headers: req.headers.set('Authorization', `Bearer ${token}`) })
    : req;

  return next(authReq).pipe(
    catchError((err: HttpErrorResponse) => {
      // Token expiré ou invalide : on ferme la session et on renvoie vers le login.
      // AuthService est résolu ici (et non au début) pour éviter une dépendance circulaire avec HttpClient.
      if (err.status === 401 && token && !req.url.includes('/auth/login')) {
        injector.get(AuthService).logout();
      }
      return throwError(() => err);
    })
  );
};
