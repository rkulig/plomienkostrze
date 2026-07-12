import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';

export interface ThreadSummary {
  id: number;
  title: string;
  authorDisplayName: string;
  createdAt: string;
  lastActivityAt: string;
  postCount: number;
}

export interface ForumPost {
  id: number;
  authorDisplayName: string;
  body: string;
  createdAt: string;
}

export interface ThreadDetail {
  id: number;
  title: string;
  authorDisplayName: string;
  createdAt: string;
  lastActivityAt: string;
  postCount: number;
  posts: ForumPost[];
  totalPosts: number;
}

export interface ThreadList {
  items: ThreadSummary[];
  total: number;
}

/**
 * Jedyne miejsce rozmowy SPA z API forum (/api/forum) — komponenty nie dotykają
 * HttpClient. Całe forum jest za logowaniem (S-07): odczyt i zapis wymagają
 * zalogowanego kibica; token Firebase dokleja interceptor.
 */
@Injectable({ providedIn: 'root' })
export class ForumApi {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/api/forum`;

  listThreads(page: number, size: number): Observable<ThreadList> {
    return this.http.get<ThreadList>(`${this.url}/threads`, { params: { page, size } });
  }

  getThread(id: number, page: number, size: number): Observable<ThreadDetail> {
    return this.http.get<ThreadDetail>(`${this.url}/threads/${id}`, { params: { page, size } });
  }

  createThread(request: { title: string; body: string }): Observable<ThreadDetail> {
    return this.http.post<ThreadDetail>(`${this.url}/threads`, request);
  }

  reply(threadId: number, request: { body: string }): Observable<ForumPost> {
    return this.http.post<ForumPost>(`${this.url}/threads/${threadId}/posts`, request);
  }
}
