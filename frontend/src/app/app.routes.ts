import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./news/news-list').then((m) => m.NewsList)
  },
  {
    path: 'news/:id',
    loadComponent: () => import('./news/news-detail').then((m) => m.NewsDetail)
  }
];
