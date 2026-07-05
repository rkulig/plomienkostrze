import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';

export interface NewsPostSummary {
  id: number;
  title: string;
  publishedAt: string;
  excerpt: string;
}

export interface NewsPost {
  id: number;
  title: string;
  publishedAt: string;
  content: string;
}

export interface NewsPostList {
  items: NewsPostSummary[];
  total: number;
}

/**
 * Jedyne miejsce rozmowy SPA z publicznym API newsów (GET /api/news-posts) —
 * komponenty nie dotykają HttpClient.
 */
@Injectable({ providedIn: 'root' })
export class NewsApi {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/api/news-posts`;

  list(page: number, size: number): Observable<NewsPostList> {
    return this.http.get<NewsPostList>(this.url, { params: { page, size } });
  }

  get(id: number): Observable<NewsPost> {
    return this.http.get<NewsPost>(`${this.url}/${id}`);
  }
}
