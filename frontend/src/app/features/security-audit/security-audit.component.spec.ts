import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { SecurityAuditComponent } from './security-audit.component';
import { SecurityAuditEvent } from './security-audit.models';
import { SecurityAuditService } from './security-audit.service';

describe('SecurityAuditComponent', () => {
  let component: SecurityAuditComponent;
  let fixture: ComponentFixture<SecurityAuditComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SecurityAuditComponent],
      providers: [{
        provide: SecurityAuditService,
        useValue: {
          findAll: () => of({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 }),
        },
      }],
    }).compileComponents();

    fixture = TestBed.createComponent(SecurityAuditComponent);
    component = fixture.componentInstance;
  });

  it('identifies an actorless login failure as an unauthenticated client', () => {
    expect(component.actorIdentity(event({ eventType: 'LOGIN_FAILED' })))
      .toBe('Unauthenticated client');
  });

  it('preserves the system fallback for other actorless events', () => {
    expect(component.actorIdentity(event({ eventType: 'AUDIT_RETENTION_CLEANUP' })))
      .toBe('System / unresolved');
  });

  it('shows a recorded actor email when one exists', () => {
    expect(component.actorIdentity(event({
      actorUserId: 1,
      actorEmail: 'admin@example.com',
      eventType: 'ROLES_CHANGED',
    }))).toBe('admin@example.com');
  });
});

function event(overrides: Partial<SecurityAuditEvent>): SecurityAuditEvent {
  return {
    id: 1,
    organizationId: 'staging',
    actorUserId: null,
    actorEmail: null,
    targetUserId: 2,
    targetEmail: 'recruiter@example.com',
    eventType: 'LOGIN_FAILED',
    outcome: 'FAILURE',
    details: null,
    createdAt: '2026-08-30T00:00:00Z',
    ...overrides,
  };
}
