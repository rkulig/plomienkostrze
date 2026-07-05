import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { NewsApi, NewsPost } from './news-api';

/**
 * Pełna treść wpisu renderowana jako akapity z czystego tekstu (bez innerHTML);
 * 404 z API pokazuje przyjazny komunikat z linkiem powrotnym.
 */
@Component({
  selector: 'app-news-detail',
  imports: [DatePipe, RouterLink],
  templateUrl: './news-detail.html',
  styleUrl: './news-detail.scss'
})
export class NewsDetail {
  private readonly api = inject(NewsApi);
  private readonly route = inject(ActivatedRoute);

  protected readonly post = signal<NewsPost | null>(null);
  protected readonly busy = signal(true);
  protected readonly notFound = signal(false);

  protected readonly paragraphs = computed(() => {
    const post = this.post();
    if (!post) {
      return [];
    }
    return post.content
      .split(/\n\s*\n/)
      .map((paragraph) => paragraph.trim())
      .filter((paragraph) => paragraph.length > 0);
  });

  constructor() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.api.get(id).subscribe({
      next: (post) => {
        this.post.set(post);
        this.busy.set(false);
      },
      error: () => {
        this.notFound.set(true);
        this.busy.set(false);
      }
    });
  }
}
