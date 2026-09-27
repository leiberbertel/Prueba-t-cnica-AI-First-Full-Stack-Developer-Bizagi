import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthStore } from './auth.store';

/** Requiere sesión iniciada. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthStore);
  return auth.isAuthenticated()
    ? true
    : inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Solo ADMIN (CA-03.1). El backend valida igualmente: esto es solo UX. */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthStore);
  return auth.isAdmin() ? true : inject(Router).createUrlTree(['/partidos']);
};

/** Login/registro solo para visitantes. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthStore);
  return auth.isAuthenticated() ? inject(Router).createUrlTree(['/partidos']) : true;
};
