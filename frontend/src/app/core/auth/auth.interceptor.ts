import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthStore } from './auth.store';

const API_PREFIX = '/api/';
const AUTH_PREFIX = '/api/v1/auth/';

/**
 * - Agrega el access token a las peticiones de la API.
 * - Ante un 401, intenta UN refresh (compartido entre peticiones concurrentes) y reintenta.
 * - Si el refresh falla, cierra la sesión y redirige al login.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith(API_PREFIX) || request.url.startsWith(AUTH_PREFIX)) {
    return next(request);
  }

  const auth = inject(AuthStore);
  const token = auth.accessToken();

  return next(withToken(request, token)).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || token === null) {
        return throwError(() => error);
      }
      return auth.refresh().pipe(
        catchError((refreshError: unknown) => {
          auth.endSession();
          return throwError(() => refreshError);
        }),
        switchMap((newToken) => next(withToken(request, newToken))),
      );
    }),
  );
};

function withToken<T>(request: HttpRequest<T>, token: string | null): HttpRequest<T> {
  return token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;
}
