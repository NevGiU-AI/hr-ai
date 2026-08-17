export interface SecurityAuditEvent {
  id: number;
  organizationId: string;
  actorUserId: number | null;
  actorEmail: string | null;
  targetUserId: number | null;
  targetEmail: string | null;
  eventType: string;
  outcome: 'SUCCESS' | 'FAILURE' | 'DENIED';
  details: string | null;
  createdAt: string;
}

export interface SecurityAuditPage {
  content: SecurityAuditEvent[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
