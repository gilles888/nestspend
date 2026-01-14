import { Routes } from '@angular/router';
import { AppShellComponent } from './layout/app-shell/app-shell';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () => import('./pages/register/register').then((m) => m.RegisterComponent),
  },
  {
    path: '',
    component: AppShellComponent,
    canActivate: [authGuard],
    children: [
      {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full',
      },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./pages/dashboard/dashboard').then((m) => m.DashboardComponent),
      },
      {
        path: 'transactions',
        loadComponent: () =>
          import('./pages/transactions/transactions').then((m) => m.TransactionsComponent),
      },
      {
        path: 'categories',
        loadComponent: () =>
          import('./pages/categories/categories').then((m) => m.CategoriesComponent),
      },
      {
        path: 'classification-rules',
        loadComponent: () =>
          import('./pages/classification-rules/classification-rules').then(
            (m) => m.ClassificationRulesComponent
          ),
      },
      {
        path: 'accounts',
        loadComponent: () =>
          import('./pages/accounts/accounts').then((m) => m.AccountsComponent),
      },
    ],
  },
];
