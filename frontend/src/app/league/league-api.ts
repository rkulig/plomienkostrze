import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';

export interface StandingRow {
  position: number | null;
  team: string;
  played: number;
  points: number;
}

export interface LeagueTable {
  rows: StandingRow[];
}

/**
 * Jedyne miejsce rozmowy SPA z API tabeli (/api/league-table) — komponent nie
 * dotyka HttpClient. Odczyt publiczny, bez tokena (mirror NewsApi, S-05).
 */
@Injectable({ providedIn: 'root' })
export class LeagueApi {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/api/league-table`;

  getTable(): Observable<LeagueTable> {
    return this.http.get<LeagueTable>(this.url);
  }
}
