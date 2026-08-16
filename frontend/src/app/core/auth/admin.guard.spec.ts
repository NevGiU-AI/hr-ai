import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, RouterStateSnapshot } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from './auth.service';
import { adminGuard } from './admin.guard';

describe('adminGuard', () => {
  const auth = {
    user: { id: 1, email: 'admin@example.com', organizationId: 'default', roles: ['ADMIN'] },
    ensureAuthenticated: jasmine.createSpy('ensureAuthenticated'),
  };

  beforeEach(() => {
    auth.user = { id: 1, email: 'admin@example.com', organizationId: 'default', roles: ['ADMIN'] };
    auth.ensureAuthenticated.and.returnValue(of(true));
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    });
  });

  function runGuard(url = '/admin/users') {
    return TestBed.runInInjectionContext(() =>
      adminGuard({} as never, { url } as RouterStateSnapshot),
    );
  }

  it('allows authenticated administrators', (done) => {
    const result = runGuard();
    if (typeof result === 'boolean') return done.fail('Expected an observable');
    (result as ReturnType<typeof of>).subscribe((allowed) => {
      expect(allowed).toBeTrue();
      done();
    });
  });

  it('redirects authenticated non-administrators to the application home', (done) => {
    auth.user = { ...auth.user, roles: ['RECRUITER'] };
    const router = TestBed.inject(Router);
    (runGuard() as ReturnType<typeof of>).subscribe((result) => {
      expect(router.serializeUrl(result as never)).toBe('/jobs/job-offer');
      done();
    });
  });

  it('redirects unauthenticated users to login with the requested URL', (done) => {
    auth.ensureAuthenticated.and.returnValue(of(false));
    const router = TestBed.inject(Router);
    (runGuard() as ReturnType<typeof of>).subscribe((result) => {
      expect(router.serializeUrl(result as never)).toBe('/login?returnUrl=%2Fadmin%2Fusers');
      done();
    });
  });
});
