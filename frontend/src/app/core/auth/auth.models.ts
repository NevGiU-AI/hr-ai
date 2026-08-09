export interface AuthUser {
  id: number;
  email: string;
  organizationId: string;
  roles: string[];
}

export interface CsrfResponse {
  token: string;
  headerName: string;
  parameterName: string;
}
