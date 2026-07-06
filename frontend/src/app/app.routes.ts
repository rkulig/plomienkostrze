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
  {
    path: 'admin',
    loadComponent: () => import('./admin/admin-panel').then((m) => m.AdminPanel)
  },
  {
    path: '**',
    redirectTo: ''
  }
];
