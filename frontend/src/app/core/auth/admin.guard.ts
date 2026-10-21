import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Sin sesión de administrador lleva al login, que después abre el panel de inicio. Solo cuando la sesión
 * expira a mitad de una tarea (ver authHttpInterceptor) el login devuelve a la página donde se estaba.
 */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.checkSession().pipe(
    map(session => session?.roles.includes('ADMIN') ? true : router.createUrlTree(['/login'])),
    catchError(() => of(router.createUrlTree(['/login'])))
  );
};
