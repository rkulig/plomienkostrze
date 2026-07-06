import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { from, switchMap } from 'rxjs';

import { environment } from '../../environments/environment';
import { AuthService } from './auth-service';

/**
 * Dokleja `Authorization: Bearer <Firebase ID token>` wyłącznie do żądań
 * kierowanych do API backendu; bez zalogowanego użytkownika żądanie wychodzi
 * bez zmian (publiczne GET-y działają jak w S-01).
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith(environment.apiBaseUrl)) {
    return next(req);
  }
  const auth = inject(AuthService);
  return from(auth.getIdToken()).pipe(
    switchMap((token) =>
      next(token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req),
    ),
  );
};
