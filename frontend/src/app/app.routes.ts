import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./news/news-list').then((m) => m.NewsList)
  },
  {
    path: 'news/:id',
    loadComponent: () => import('./news/news-detail').then((m) => m.NewsDetail)
  },
  // Temporary diagnostic view (deploy-plan Phase C) — remove with the probe.
  {
    path: 'test-flow',
    loadComponent: () => import('./test-flow/test-flow').then((m) => m.TestFlow)
  }
];
