import { Component, effect, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AdminStatus } from '../auth/admin-status';
import { AuthService } from '../auth/auth-service';
import { NewsApi } from '../news/news-api';

/**
 * Formularz edycji opublikowanego wpisu (FR-007) na trasie `admin/edit/:id`.
 * Reużywa konwencje z AdminPanel (reactive form, walidatory, progres „…",
 * błąd inline, gating admina z redirectem). Formularz wypełniany jest jeden
 * raz po wczytaniu posta — brak asynchronicznego nadpisania po interakcji
 * (w odróżnieniu od patchValue z generacji AI w AdminPanel).
 */
@Component({
  selector: 'app-post-edit',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './post-edit.html',
  styleUrl: './post-edit.scss'
})
export class PostEdit {
  private readonly authService = inject(AuthService);
  private readonly adminStatus = inject(AdminStatus);
  private readonly newsApi = inject(NewsApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  private readonly id = Number(this.route.snapshot.paramMap.get('id'));

  protected readonly ready = this.adminStatus.isAdmin;
  protected readonly loading = signal(true);
  protected readonly notFound = signal(false);
  protected readonly saving = signal(false);
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

    if (!Number.isInteger(this.id) || this.id <= 0) {
      this.notFound.set(true);
      this.loading.set(false);
    } else {
      this.newsApi.get(this.id).subscribe({
        next: (post) => {
          this.form.setValue({ title: post.title, content: post.content });
          this.loading.set(false);
        },
        error: (err: HttpErrorResponse) => {
          if (err.status === 404 || err.status === 400) {
            this.notFound.set(true);
          } else {
            this.error.set(`Nie udało się pobrać wpisu (HTTP ${err.status})`);
          }
          this.loading.set(false);
        }
      });
    }
  }

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const { title, content } = this.form.getRawValue();
    this.newsApi.update(this.id, title, content).subscribe({
      next: () => this.router.navigate(['/news', this.id]),
      error: (err: HttpErrorResponse) => {
        this.error.set(`Nie udało się zapisać wpisu (HTTP ${err.status})`);
        this.saving.set(false);
      }
    });
  }
}
