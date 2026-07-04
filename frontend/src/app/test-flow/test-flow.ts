import { Component, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';

import { environment } from '../../environments/environment';

interface TestMessage {
  id: number;
  content: string;
  createdAt: string;
}

/**
 * Temporary diagnostic view (deploy-plan Phase C): submits a text to the API
 * and lists what actually landed in the database — proving the full
 * SPA -> API -> Cloud SQL data path. Removed once real features land.
 */
@Component({
  selector: 'app-test-flow',
  imports: [DatePipe],
  templateUrl: './test-flow.html',
  styleUrl: './test-flow.scss'
})
export class TestFlow {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/api/test-messages`;

  protected readonly content = signal('');
  protected readonly messages = signal<TestMessage[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.refresh();
  }

  protected onInput(event: Event): void {
    this.content.set((event.target as HTMLInputElement).value);
  }

  protected send(): void {
    const text = this.content().trim();
    if (!text || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.http.post<TestMessage>(this.url, { content: text }).subscribe({
      next: () => {
        this.content.set('');
        this.refresh();
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(`Zapis nie powiódł się (HTTP ${err.status})`);
        this.busy.set(false);
      }
    });
  }

  protected refresh(): void {
    this.http.get<TestMessage[]>(this.url).subscribe({
      next: (messages) => {
        this.messages.set(messages);
        this.busy.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(`Odczyt nie powiódł się (HTTP ${err.status})`);
        this.busy.set(false);
      }
    });
  }
}
