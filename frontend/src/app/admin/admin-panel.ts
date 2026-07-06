import { Component, effect, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { AdminStatus } from '../auth/admin-status';
import { AuthService } from '../auth/auth-service';
import { NewsApi } from '../news/news-api';

/**
 * Formularz nowego wpisu (FR-006) — jedyny stan tego widoku; logowanie żyje
 * w nagłówku. Deep-link bez uprawnień (wylogowany albo rozstrzygnięte
 * isAdmin=false) przekierowuje na `/`; przy nierozstrzygniętym statusie (null)
 * czekamy — obrona i tak jest w backendzie.
 */
@Component({
  selector: 'app-admin-panel',
  imports: [ReactiveFormsModule],
  templateUrl: './admin-panel.html',
  styleUrl: './admin-panel.scss'
})
export class AdminPanel {
  private readonly authService = inject(AuthService);
  private readonly adminStatus = inject(AdminStatus);
  private readonly newsApi = inject(NewsApi);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly ready = this.adminStatus.isAdmin;
  protected readonly sending = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.formBuilder.group({
    title: ['', [Validators.required, Validators.maxLength(200)]],
    content: ['', [Validators.required, Validators.maxLength(10000)]]
  });

  constructor() {
    effect(() => {
      const user = this.authService.user();
      const isAdmin = this.adminStatus.isAdmin();
      if (user === null || isAdmin === false) {
        this.router.navigate(['']);
      }
    });
  }

  protected publish(): void {
    if (this.form.invalid || this.sending()) {
      return;
    }
    this.sending.set(true);
    this.error.set(null);
    const { title, content } = this.form.getRawValue();
    this.newsApi.create(title, content).subscribe({
      next: (post) => this.router.navigate(['/news', post.id]),
      error: (err: HttpErrorResponse) => {
        this.error.set(`Nie udało się opublikować wpisu (HTTP ${err.status})`);
        this.sending.set(false);
      }
    });
  }
}
