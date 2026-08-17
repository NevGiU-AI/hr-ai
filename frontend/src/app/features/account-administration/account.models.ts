export const ACCOUNT_ROLES = ['ADMIN', 'RECRUITER', 'REVIEWER', 'READ_ONLY'] as const;
export type AccountRole = typeof ACCOUNT_ROLES[number];

export interface Account {
  id: number;
  email: string;
  organizationId: string;
  enabled: boolean;
  roles: AccountRole[];
  locked: boolean;
  lockoutRemainingSeconds: number;
}

export interface CreateAccountRequest {
  email: string;
  password: string;
  roles: AccountRole[];
}

export interface RevokeSessionsResponse {
  revokedSessions: number;
}
