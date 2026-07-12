import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';

import { FixturesApi, FixtureRow } from './fixtures-api';

/**
 * Publiczny terminarz (S-06): data/kolejka, przeciwnik, miejsce, wynik — mecze
 * Płomienia scrapowane z 90minut.pl przez backend. Stan ładowania/błędu/pustki
 * jak w LeagueTable.
 */
@Component({
  selector: 'app-fixtures',
  templateUrl: './fixtures.html',
  styleUrl: './fixtures.scss'
})
export class Fixtures {
  private readonly api = inject(FixturesApi);

  protected readonly rows = signal<FixtureRow[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.load();
  }

  protected load(): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.getFixtures().subscribe({
      next: (fixtures) => {
        this.rows.set(fixtures.rows);
        this.busy.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(`Nie udało się pobrać terminarza (HTTP ${err.status})`);
        this.busy.set(false);
      }
    });
  }
}
