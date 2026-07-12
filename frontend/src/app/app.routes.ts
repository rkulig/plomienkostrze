import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./news/news-list').then((m) => m.NewsList)
  },
  {
    path: 'tabela',
    loadComponent: () => import('./league/league-table').then((m) => m.LeagueTable)
  },
  {
    path: 'terminarz',
    loadComponent: () => import('./fixtures/fixtures').then((m) => m.Fixtures)
  },
  {
    path: 'news/:id',
    loadComponent: () => import('./news/news-detail').then((m) => m.NewsDetail)
  },
  {
    path: 'forum',
    loadComponent: () => import('./forum/thread-list').then((m) => m.ThreadList)
  },
  {
    path: 'forum/nowy',
    loadComponent: () => import('./forum/new-thread').then((m) => m.NewThread)
  },
  {
    path: 'forum/:id',
    loadComponent: () => import('./forum/thread-detail').then((m) => m.ThreadDetail)
  },
  {
    path: 'admin',
    loadComponent: () => import('./admin/admin-panel').then((m) => m.AdminPanel)
  },
  {
    path: 'admin/edit/:id',
    loadComponent: () => import('./admin/post-edit').then((m) => m.PostEdit)
  },
  {
    path: '**',
    redirectTo: ''
  }
];
