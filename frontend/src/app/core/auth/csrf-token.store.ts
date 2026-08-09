import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class CsrfTokenStore {
  token = '';
  headerName = 'X-XSRF-TOKEN';
}
