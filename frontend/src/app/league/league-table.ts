import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';

import { LeagueApi, StandingRow } from './league-api';

/**
 * Publiczna tabela ligowa (S-05): pozycja, drużyna, mecze, punkty — scrapowana
 * z 90minut.pl przez backend. Stan ładowania/błędu/pustki jak w NewsList.
 */
@Component({
  selector: 'app-league-table',
  templateUrl: './league-table.html',
  styleUrl: './league-table.scss'
})
export class LeagueTable {
  private readonly api = inject(LeagueApi);

  protected readonly rows = signal<StandingRow[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.load();
  }

  protected load(): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.getTable().subscribe({
      next: (table) => {
        this.rows.set(table.rows);
        this.busy.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(`Nie udało się pobrać tabeli (HTTP ${err.status})`);
        this.busy.set(false);
      }
    });
  }
}
