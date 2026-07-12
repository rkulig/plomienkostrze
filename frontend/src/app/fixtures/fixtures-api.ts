import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';

export interface FixtureRow {
  round: string;
  opponent: string;
  home: boolean;
  played: boolean;
  goalsFor: number | null;
  goalsAgainst: number | null;
}

export interface Fixtures {
  rows: FixtureRow[];
}

/**
 * Jedyne miejsce rozmowy SPA z API terminarza (/api/fixtures) — komponent nie
 * dotyka HttpClient. Odczyt publiczny, bez tokena (mirror LeagueApi, S-06).
 */
@Injectable({ providedIn: 'root' })
export class FixturesApi {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/api/fixtures`;

  getFixtures(): Observable<Fixtures> {
    return this.http.get<Fixtures>(this.url);
  }
}
