import { Routes } from '@angular/router';

export const routes: Routes = [
  // Temporary diagnostic view (deploy-plan Phase C) — remove with the probe.
  {
    path: 'test-flow',
    loadComponent: () => import('./test-flow/test-flow').then((m) => m.TestFlow)
  }
];
