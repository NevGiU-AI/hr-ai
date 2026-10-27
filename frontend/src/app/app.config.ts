import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withInMemoryScrolling } from '@angular/router';

import { routes } from './app.routes';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { securityInterceptor } from './core/auth/security.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideHttpClient(withInterceptors([securityInterceptor])),
    // Citation links use route fragments; enable router-managed anchor scrolling for same-page navigation.
    provideRouter(routes, withInMemoryScrolling({ anchorScrolling: 'enabled' })),
  ]
};
