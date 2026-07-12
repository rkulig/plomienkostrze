import { Component, effect, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../auth/auth-service';
import { authorLabel } from './author-label';
import { ForumApi, ForumPost, ThreadDetail as ThreadDetailDto } from './forum-api';

const POSTS_PAGE_SIZE = 50;

/**
 * Wątek: post otwierający i wszystkie odpowiedzi chronologicznie, plus formularz
 * odpowiedzi (S-07). Całe forum jest za logowaniem — wylogowany gość jest
 * odsyłany na stronę główną, dane ładują się dopiero po zalogowaniu. 404 z API
 * pokazuje przyjazny komunikat z linkiem powrotnym.
 */
@Component({
  selector: 'app-thread-detail',
  imports: [DatePipe, RouterLink, ReactiveFormsModule],
  templateUrl: './thread-detail.html',
  styleUrl: './thread-detail.scss'
})
export class ThreadDetail {
  private readonly api = inject(ForumApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly id = Number(this.route.snapshot.paramMap.get('id'));
  private started = false;

  protected readonly authorLabel = authorLabel;
  protected readonly thread = signal<ThreadDetailDto | null>(null);
  protected readonly posts = signal<ForumPost[]>([]);
  protected readonly busy = signal(true);
  protected readonly notFound = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly sending = signal(false);
  protected readonly replyError = signal<string | null>(null);

  protected readonly form = this.formBuilder.group({
    body: ['', [Validators.required, Validators.maxLength(10000)]]
  });

  constructor() {
    effect(() => {
      const user = this.authService.user();
      if (user === null) {
        this.router.navigate(['']);
        return;
      }
      if (user && !this.started) {
        this.started = true;
        this.load();
      }
    });
  }

  private load(): void {
    if (!Number.isInteger(this.id) || this.id <= 0) {
      this.notFound.set(true);
      this.busy.set(false);
      return;
    }
    this.api.getThread(this.id, 0, POSTS_PAGE_SIZE).subscribe({
      next: (thread) => {
        this.thread.set(thread);
        this.posts.set(thread.posts);
        this.busy.set(false);
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 404 || err.status === 400) {
          this.notFound.set(true);
        } else {
          this.error.set(`Nie udało się pobrać wątku (HTTP ${err.status})`);
        }
        this.busy.set(false);
      }
    });
  }

  protected submitReply(): void {
    if (this.form.invalid || this.sending()) {
      return;
    }
    this.sending.set(true);
    this.replyError.set(null);
    const { body } = this.form.getRawValue();
    this.api.reply(this.id, { body }).subscribe({
      next: (post) => {
        this.posts.update((current) => [...current, post]);
        this.thread.update((thread) =>
          thread ? { ...thread, postCount: thread.postCount + 1 } : thread
        );
        this.form.reset();
        this.sending.set(false);
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 404) {
          this.replyError.set('Ten wątek już nie istnieje.');
        } else {
          this.replyError.set(`Nie udało się dodać odpowiedzi (HTTP ${err.status})`);
        }
        this.sending.set(false);
      }
    });
  }
}
