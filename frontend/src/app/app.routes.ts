import { Routes } from '@angular/router';

import { adminGuard, authGuard, guestGuard } from './core/auth/auth.guards';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Iniciar sesión · Polla Mundialista',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'registro',
    title: 'Crear cuenta · Polla Mundialista',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register.page').then((m) => m.RegisterPage),
  },
  {
    path: '',
    loadComponent: () => import('./core/layout/shell').then((m) => m.Shell),
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'partidos' },
      {
        path: 'partidos',
        title: 'Partidos · Polla Mundialista',
        loadComponent: () => import('./features/matches/matches.page').then((m) => m.MatchesPage),
      },
      {
        path: 'ranking',
        title: 'Ranking · Polla Mundialista',
        loadComponent: () => import('./features/leaderboard/leaderboard.page').then((m) => m.LeaderboardPage),
      },
      {
        path: 'ranking/:userId',
        title: 'Historial · Polla Mundialista',
        loadComponent: () => import('./features/leaderboard/history.page').then((m) => m.HistoryPage),
      },
      {
        path: 'admin',
        title: 'Administración · Polla Mundialista',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/admin/admin.page').then((m) => m.AdminPage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
