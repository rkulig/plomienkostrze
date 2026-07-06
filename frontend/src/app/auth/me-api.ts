import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';

export interface Me {
  admin: boolean;
}

/**
 * Status zalogowanego użytkownika z backendu (GET /api/me) — allowlist
 * adminów żyje wyłącznie po stronie backendu, SPA tylko pyta.
 */
@Injectable({ providedIn: 'root' })
export class MeApi {
  private readonly http = inject(HttpClient);

  get(): Observable<Me> {
    return this.http.get<Me>(`${environment.apiBaseUrl}/api/me`);
  }
}
