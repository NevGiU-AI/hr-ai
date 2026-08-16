export const ACCOUNT_ROLES = ['ADMIN', 'RECRUITER', 'REVIEWER', 'READ_ONLY'] as const;
export type AccountRole = typeof ACCOUNT_ROLES[number];

export interface Account {
  id: number;
  email: string;
  organizationId: string;
  enabled: boolean;
  roles: AccountRole[];
}

export interface CreateAccountRequest {
  email: string;
  password: string;
  roles: AccountRole[];
}
