import { Injectable, effect, inject, signal } from '@angular/core';

import { AuthService } from './auth-service';
import { MeApi } from './me-api';

/**
 * Wspólny sygnał statusu admina dla nagłówka („Dodaj post") i widoku /admin
 * (redirect): po każdym zalogowaniu woła GET /api/me raz i wystawia
 * `isAdmin` — null = nierozstrzygnięte (ładowanie / wylogowany), boolean =
 * odpowiedź backendu. Konsumenci reagują wyłącznie na rozstrzygnięte wartości.
 */
@Injectable({ providedIn: 'root' })
export class AdminStatus {
  private readonly auth = inject(AuthService);
  private readonly meApi = inject(MeApi);

  readonly isAdmin = signal<boolean | null>(null);

  constructor() {
    effect(() => {
      const user = this.auth.user();
      this.isAdmin.set(null);
      if (!user) {
        return; // SDK się inicjalizuje (undefined) albo wylogowany (null)
      }
      this.meApi.get().subscribe({
        next: (me) => this.isAdmin.set(me.admin),
        error: () => this.isAdmin.set(false),
      });
    });
  }
}
