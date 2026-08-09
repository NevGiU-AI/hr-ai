import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { CsrfTokenStore } from './csrf-token.store';

export const securityInterceptor: HttpInterceptorFn = (request, next) => {
  const csrf = inject(CsrfTokenStore);
  const mutating = !['GET', 'HEAD', 'OPTIONS'].includes(request.method.toUpperCase());
  let secured = request.clone({ withCredentials: true });
  if (mutating && csrf.token) {
    secured = secured.clone({ setHeaders: { [csrf.headerName]: csrf.token } });
  }
  return next(secured);
};
