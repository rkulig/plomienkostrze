import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../auth/auth-service';
import { ForumApi } from './forum-api';
import { ForumLoginGate } from './forum-login-gate';

/**
 * Formularz nowego wątku (tytuł + treść otwierająca, S-07). Gated na zalogowanie
 * (nie na admina): wylogowany gość widzi bramkę z logowaniem zamiast formularza.
 * Po sukcesie nawiguje do świeżo utworzonego wątku; obrona i tak jest w backendzie.
 */
@Component({
  selector: 'app-new-thread',
  imports: [ReactiveFormsModule, RouterLink, ForumLoginGate],
  templateUrl: './new-thread.html',
  styleUrl: './new-thread.scss'
})
export class NewThread {
  private readonly api = inject(ForumApi);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly user = this.authService.user;
  protected readonly sending = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.formBuilder.group({
    title: ['', [Validators.required, Validators.maxLength(200)]],
    body: ['', [Validators.required, Validators.maxLength(10000)]]
  });

  protected create(): void {
    if (this.form.invalid || this.sending()) {
      return;
    }
    this.sending.set(true);
    this.error.set(null);
    const { title, body } = this.form.getRawValue();
    this.api.createThread({ title, body }).subscribe({
      next: (thread) => this.router.navigate(['/forum', thread.id]),
      error: (err: HttpErrorResponse) => {
        this.error.set(`Nie udało się założyć wątku (HTTP ${err.status})`);
        this.sending.set(false);
      }
    });
  }
}
