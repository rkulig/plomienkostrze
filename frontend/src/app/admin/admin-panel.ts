import { Component, effect, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AdminStatus } from '../auth/admin-status';
import { AuthService } from '../auth/auth-service';
import { NewsApi } from '../news/news-api';

/**
 * Formularz nowego wpisu (FR-006) — jedyny stan tego widoku; logowanie żyje
 * w nagłówku. Deep-link bez uprawnień (wylogowany albo rozstrzygnięte
 * isAdmin=false) przekierowuje na `/`; przy nierozstrzygniętym statusie (null)
 * czekamy — obrona i tak jest w backendzie.
 *
 * S-03: druga ścieżka tworzenia wpisu — „Generuj z ostatniego meczu" wypełnia
 * te same kontrolki propozycją z backendu. Propozycja żyje wyłącznie tutaj
 * (odświeżenie strony ją traci); publikacja to zwykłe publish(), odrzucenie
 * niczego nie zapisuje.
 */
@Component({
  selector: 'app-admin-panel',
  imports: [ReactiveFormsModule, RouterLink],
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
  protected readonly generating = signal(false);
  protected readonly generationError = signal<string | null>(null);
  protected readonly hasProposal = signal(false);

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

  protected generate(): void {
    if (this.generating()) {
      return;
    }
    this.generating.set(true);
    this.generationError.set(null);
    this.newsApi.generateFromLastMatch().subscribe({
      next: (proposal) => {
        this.form.patchValue({ title: proposal.title, content: proposal.content });
        this.hasProposal.set(true);
        this.generating.set(false);
      },
      error: (err: HttpErrorResponse) => {
        // 424 = backend nie zdobył danych meczu (scraper); reszta = błąd generacji.
        this.generationError.set(
          err.status === 424
            ? 'Nie udało się pobrać danych ostatniego meczu (HTTP 424)'
            : `Nie udało się wygenerować propozycji (HTTP ${err.status})`
        );
        this.generating.set(false);
      }
    });
  }

  protected reject(): void {
    this.form.reset();
    this.hasProposal.set(false);
    this.generationError.set(null);
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
