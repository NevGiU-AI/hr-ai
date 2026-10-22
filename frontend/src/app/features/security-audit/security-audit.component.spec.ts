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

  it('shows the safe candidate ID as the target of candidate erasure', () => {
    expect(component.targetIdentity(event({
      targetUserId: null,
      targetEmail: null,
      eventType: 'CANDIDATE_ERASED',
      details: 'candidateId=47;originals=0',
    }))).toBe('Candidate #47');
  });

  it('preserves user targets for account events', () => {
    expect(component.targetIdentity(event({
      eventType: 'ROLES_CHANGED',
    }))).toBe('recruiter@example.com');
  });

  it('uses the system fallback when candidate details are malformed', () => {
    expect(component.targetIdentity(event({
      targetUserId: null,
      targetEmail: null,
      eventType: 'CANDIDATE_ERASURE_FAILED',
      details: 'reason=storage-unavailable',
    }))).toBe('System / unresolved');
  });

  it('explains when an erased candidate had no stored original', () => {
    expect(component.eventDetails(event({
      eventType: 'CANDIDATE_ERASED',
      details: 'candidateId=47;originals=0',
    }))).toBe('No stored original file was associated');
  });

  it('reports how many stored originals were deleted', () => {
    expect(component.eventDetails(event({
      eventType: 'CANDIDATE_ERASED',
      details: 'candidateId=47;storedOriginalsDeleted=2',
    }))).toBe('2 stored original files deleted');
  });

  it('shows candidate target and friendly reprocessing details', () => {
    const reprocessed = event({
      targetUserId: null,
      targetEmail: null,
      eventType: 'CV_DOCUMENT_REPROCESSED',
      details: 'candidateId=47;documentId=9;evaluationsInvalidated=2',
    });
    expect(component.targetIdentity(reprocessed)).toBe('Candidate #47');
    expect(component.eventDetails(reprocessed))
      .toBe('Document #9 reprocessed; 2 prior evaluations marked stale');
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
