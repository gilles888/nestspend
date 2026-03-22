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
        path: 'projections',
        loadComponent: () =>
          import('./pages/projections/projections').then((m) => m.ProjectionsComponent),
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
      // Route vers la gestion des budgets par catégorie
      {
        path: 'budgets',
        loadComponent: () =>
          import('./pages/budgets/budgets').then((m) => m.BudgetsComponent),
      },
      // Route vers le tableau de projection des dépenses (tableur mensuel/annuel)
      {
        path: 'expense-table',
        loadComponent: () =>
          import('./pages/expense-table/expense-table').then((m) => m.ExpenseTableComponent),
      },
      // Route vers le tableau de projection annuelle avec cellules éditables (Jan → Déc)
      {
        path: 'tableau-projection',
        loadComponent: () =>
          import('./pages/tableau-projection/tableau-projection').then(
            (m) => m.TableauProjectionComponent
          ),
      },
      // Route vers la page de visualisation graphique (courbe épargne, camembert, histogramme)
      {
        path: 'graphiques',
        loadComponent: () =>
          import('./pages/graphiques/graphiques').then((m) => m.GraphiquesComponent),
      },
    ],
  },
];
