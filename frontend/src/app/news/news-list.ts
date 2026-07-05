import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';

import { NewsApi, NewsPostSummary } from './news-api';

const PAGE_SIZE = 10;

/**
 * Domyślny widok aplikacji: opublikowane wpisy od najnowszego (tytuł-link,
 * data, zajawka) z doładowywaniem starszych stron przyciskiem.
 */
@Component({
  selector: 'app-news-list',
  imports: [DatePipe, RouterLink],
  templateUrl: './news-list.html',
  styleUrl: './news-list.scss'
})
export class NewsList {
  private readonly api = inject(NewsApi);
  private nextPage = 0;

  protected readonly items = signal<NewsPostSummary[]>([]);
  protected readonly total = signal(0);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.loadMore();
  }

  protected loadMore(): void {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.list(this.nextPage, PAGE_SIZE).subscribe({
      next: (page) => {
        this.items.update((current) => [...current, ...page.items]);
        this.total.set(page.total);
        this.nextPage++;
        this.busy.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(`Nie udało się pobrać aktualności (HTTP ${err.status})`);
        this.busy.set(false);
      }
    });
  }

  protected hasMore(): boolean {
    return this.items().length < this.total();
  }
}
