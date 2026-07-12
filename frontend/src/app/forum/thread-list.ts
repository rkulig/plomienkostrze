import { Component, effect, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';

import { AuthService } from '../auth/auth-service';
import { authorLabel } from './author-label';
import { ForumApi, ThreadSummary } from './forum-api';
import { ForumLoginGate } from './forum-login-gate';

const PAGE_SIZE = 10;

/**
 * Lista wątków forum od najświeższej aktywności (S-07). Całe forum jest za
 * logowaniem: wylogowany gość (user === null) widzi bramkę z komunikatem i
 * przyciskiem logowania — żaden request forum nie wychodzi, więc nic nie
 * wycieka. Ładowanie startuje dopiero po zalogowaniu.
 */
@Component({
  selector: 'app-thread-list',
  imports: [DatePipe, RouterLink, ForumLoginGate],
  templateUrl: './thread-list.html',
  styleUrl: './thread-list.scss'
})
export class ThreadList {
  private readonly api = inject(ForumApi);
  private readonly authService = inject(AuthService);
  private nextPage = 0;
  private started = false;

  protected readonly user = this.authService.user;
  protected readonly authorLabel = authorLabel;
  protected readonly items = signal<ThreadSummary[]>([]);
  protected readonly total = signal(0);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      const user = this.authService.user();
      if (user && !this.started) {
        this.started = true;
        this.loadMore();
      }
    });
  }

  protected loadMore(): void {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.listThreads(this.nextPage, PAGE_SIZE).subscribe({
      next: (page) => {
        this.items.update((current) => [
          ...current,
          ...page.items.filter((item) => !current.some((existing) => existing.id === item.id))
        ]);
        this.total.set(page.total);
        this.nextPage++;
        this.busy.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(`Nie udało się pobrać wątków (HTTP ${err.status})`);
        this.busy.set(false);
      }
    });
  }

  protected hasMore(): boolean {
    return this.items().length < this.total();
  }
}
