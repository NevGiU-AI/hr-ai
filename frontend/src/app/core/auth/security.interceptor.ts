import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthSessionState } from './auth-session-state.service';
import { CsrfTokenStore } from './csrf-token.store';

export const securityInterceptor: HttpInterceptorFn = (request, next) => {
  const csrf = inject(CsrfTokenStore);
  const session = inject(AuthSessionState);
  const mutating = !['GET', 'HEAD', 'OPTIONS'].includes(request.method.toUpperCase());
  let secured = request.clone({ withCredentials: true });
  if (mutating && csrf.token) {
    secured = secured.clone({ setHeaders: { [csrf.headerName]: csrf.token } });
  }
  return next(secured).pipe(
    catchError((error: HttpErrorResponse) => {
      const isLoginRequest = request.url.endsWith('/auth/login');
      if (error.status === 401 && !isLoginRequest) session.expire(true, true);
      return throwError(() => error);
    }),
  );
};
