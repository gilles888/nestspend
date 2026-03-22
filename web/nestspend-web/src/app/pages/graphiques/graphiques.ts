import { Component, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { CommonModule, CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { Subject } from 'rxjs';

import { ChartModule } from 'primeng/chart';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';

import { DashboardService } from '../../core/api/services/dashboard.service';
import { CategoriesService } from '../../core/api/services/categories.service';
import { DashboardResponse } from '../../core/api/models/dashboard-response';
import { CategoryResponse } from '../../core/api/models/category-response';

// Palette de couleurs prédéfinie pour les graphiques
const CHART_COLORS = [
  '#3B82F6', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6',
  '#EC4899', '#06B6D4', '#84CC16', '#F97316', '#6366F1',
  '#14B8A6', '#D97706', '#DC2626', '#7C3AED', '#DB2777',
];

/**
 * Structure représentant les données d'un mois chargées depuis l'API.
 */
interface MonthData {
  /** Clé du mois au format YYYY-MM */
  key: string;
  /** Libellé du mois ex: "Jan" */
  label: string;
  /** Libellé long du mois ex: "Janvier 2025" */
  labelLong: string;
  /** Données du dashboard pour ce mois */
  data: DashboardResponse | null;
  /** Indique si le chargement est en cours */
  loading: boolean;
}

/**
 * Page de visualisation graphique des finances personnelles.
 *
 * Propose trois graphiques :
 * 1. Courbe d'épargne mensuelle (revenus - dépenses sur N mois)
 * 2. Camembert des dépenses par catégorie (pour le mois sélectionné)
 * 3. Histogramme revenus vs dépenses par mois (N derniers mois)
 *
 * Utilise l'API /api/dashboard?month=YYYY-MM pour chaque mois chargé.
 */
@Component({
  selector: 'app-graphiques',
  standalone: true,
  imports: [
    CommonModule,
    CurrencyPipe,
    FormsModule,
    TranslateModule,
    ChartModule,
    ButtonModule,
    SelectModule,
    SelectButtonModule,
    ToastModule,
  ],
  providers: [MessageService],
  templateUrl: './graphiques.html',
  styleUrl: './graphiques.scss',
})
export class GraphiquesComponent implements OnInit, OnDestroy {
  // Sujet de destruction pour éviter les fuites mémoire sur les subscriptions
  private readonly destroy$ = new Subject<void>();

  loading = signal(false);

  // Données chargées par mois
  months = signal<MonthData[]>([]);
  categories = signal<CategoryResponse[]>([]);

  // Mois sélectionné pour le camembert des dépenses par catégorie
  selectedMonth = signal<string>(this.getCurrentMonth());
  monthSelectOptions: { label: string; value: string }[] = [];

  // Période (6 ou 12 mois) pour les graphiques courbe et histogramme
  selectedPeriod = signal<number>(6);
  periodOptions = [
    { label: '6 mois', value: 6 },
    { label: '12 mois', value: 12 },
  ];

  // Cache des données par mois pour éviter les requêtes répétées
  private dashboardCache = new Map<string, DashboardResponse>();

  // Données pour la courbe d'épargne mensuelle
  savingsChartData = computed(() => {
    const months = this.months().filter((m) => m.data !== null);
    if (months.length === 0) return null;

    const labels = months.map((m) => m.label);
    const savingsData = months.map((m) => {
      const income = (m.data?.totalIncomeCents ?? 0) / 100;
      const expenses = (m.data?.totalExpenseCents ?? 0) / 100;
      return income - expenses;
    });

    return {
      labels,
      datasets: [
        {
          label: 'Épargne mensuelle',
          data: savingsData,
          fill: true,
          borderColor: '#10B981',
          backgroundColor: 'rgba(16, 185, 129, 0.15)',
          tension: 0.4,
          pointRadius: 5,
          pointHoverRadius: 8,
          pointBackgroundColor: savingsData.map((v) => (v >= 0 ? '#10B981' : '#EF4444')),
          pointBorderColor: savingsData.map((v) => (v >= 0 ? '#10B981' : '#EF4444')),
          segment: {
            borderColor: (ctx: { p0: { parsed: { y: number } }; p1: { parsed: { y: number } } }) =>
              ctx.p0.parsed.y >= 0 && ctx.p1.parsed.y >= 0 ? '#10B981' : '#EF4444',
          },
        },
      ],
    };
  });

  savingsChartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: { intersect: false, mode: 'index' as const },
    plugins: {
      legend: { position: 'top' as const },
      tooltip: {
        callbacks: {
          label: (context: { dataset: { label: string }; raw: number }) =>
            `${context.dataset.label}: €${(context.raw as number).toFixed(2)}`,
        },
      },
    },
    scales: {
      y: {
        beginAtZero: false,
        ticks: {
          callback: (value: number | string) => `€${Number(value).toFixed(0)}`,
        },
        grid: {
          color: (context: { tick: { value: number } }) =>
            context.tick.value === 0 ? 'rgba(0,0,0,0.3)' : 'rgba(0,0,0,0.1)',
          lineWidth: (context: { tick: { value: number } }) =>
            context.tick.value === 0 ? 2 : 1,
        },
      },
    },
  };

  // Données pour le camembert des dépenses par catégorie
  pieChartData = computed(() => {
    const monthData = this.months().find((m) => m.key === this.selectedMonth());
    if (!monthData?.data?.expensesByCategory?.length) return null;

    const expenses = monthData.data.expensesByCategory;
    const categoryMap = new Map<string, CategoryResponse>(
      this.categories().map((c) => [c.id ?? '', c])
    );

    const labels = expenses.map((e) => e.categoryName ?? 'Inconnu');
    const data = expenses.map((e) => (e.amountCents ?? 0) / 100);
    const colors = expenses.map((e, i) => {
      const cat = categoryMap.get(e.categoryId ?? '');
      return cat?.color ?? CHART_COLORS[i % CHART_COLORS.length];
    });

    return {
      labels,
      datasets: [
        {
          data,
          backgroundColor: colors,
          hoverBackgroundColor: colors.map((c) => c + 'CC'),
          borderWidth: 2,
          borderColor: '#ffffff',
        },
      ],
    };
  });

  pieChartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'right' as const,
        labels: { usePointStyle: true, padding: 15, font: { size: 12 } },
      },
      tooltip: {
        callbacks: {
          label: (context: { label: string; raw: number; dataset: { data: number[] } }) => {
            const total = (context.dataset.data as number[]).reduce((a, b) => a + b, 0);
            const pct = total > 0 ? Math.round((context.raw / total) * 100) : 0;
            return `${context.label}: €${(context.raw as number).toFixed(2)} (${pct}%)`;
          },
        },
      },
    },
  };

  // Données pour l'histogramme revenus vs dépenses
  barChartData = computed(() => {
    const months = this.months().filter((m) => m.data !== null);
    if (months.length === 0) return null;

    const labels = months.map((m) => m.label);
    const incomeData = months.map((m) => (m.data?.totalIncomeCents ?? 0) / 100);
    const expenseData = months.map((m) => (m.data?.totalExpenseCents ?? 0) / 100);

    return {
      labels,
      datasets: [
        {
          label: 'Revenus',
          data: incomeData,
          backgroundColor: 'rgba(16, 185, 129, 0.75)',
          borderColor: '#10B981',
          borderWidth: 1,
          borderRadius: 4,
        },
        {
          label: 'Dépenses',
          data: expenseData,
          backgroundColor: 'rgba(239, 68, 68, 0.75)',
          borderColor: '#EF4444',
          borderWidth: 1,
          borderRadius: 4,
        },
      ],
    };
  });

  barChartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: { intersect: false, mode: 'index' as const },
    plugins: {
      legend: { position: 'top' as const },
      tooltip: {
        callbacks: {
          label: (context: { dataset: { label: string }; raw: number }) =>
            `${context.dataset.label}: €${(context.raw as number).toFixed(2)}`,
        },
      },
    },
    scales: {
      y: {
        beginAtZero: true,
        ticks: {
          callback: (value: number | string) => `€${Number(value).toFixed(0)}`,
        },
      },
    },
  };

  // Statistiques calculées pour les cartes de résumé
  averageSavings = computed((): number => {
    const months = this.months().filter((m) => m.data !== null);
    if (months.length === 0) return 0;
    const total = months.reduce((sum, m) => {
      const income = (m.data?.totalIncomeCents ?? 0) / 100;
      const expenses = (m.data?.totalExpenseCents ?? 0) / 100;
      return sum + (income - expenses);
    }, 0);
    return total / months.length;
  });

  bestSavingsMonth = computed((): { label: string; value: number } | null => {
    const months = this.months().filter((m) => m.data !== null);
    if (months.length === 0) return null;
    let best = months[0];
    let bestSavings = (best.data!.totalIncomeCents! - best.data!.totalExpenseCents!) / 100;
    for (const m of months) {
      const savings = ((m.data?.totalIncomeCents ?? 0) - (m.data?.totalExpenseCents ?? 0)) / 100;
      if (savings > bestSavings) {
        bestSavings = savings;
        best = m;
      }
    }
    return { label: best.labelLong, value: bestSavings };
  });

  totalSavingsPeriod = computed((): number => {
    return this.months()
      .filter((m) => m.data !== null)
      .reduce((sum, m) => {
        const income = (m.data?.totalIncomeCents ?? 0) / 100;
        const expenses = (m.data?.totalExpenseCents ?? 0) / 100;
        return sum + (income - expenses);
      }, 0);
  });

  constructor(
    private dashboardService: DashboardService,
    private categoriesService: CategoriesService,
    private messageService: MessageService
  ) {
    this.initMonthSelectOptions();
  }

  ngOnInit(): void {
    this.initMonths();
    this.loadCategories();
    this.loadAllMonths();
  }

  // Libère toutes les subscriptions lors de la destruction du composant
  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // Retourne le mois courant au format YYYY-MM
  private getCurrentMonth(): string {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
  }

  // Initialise les options du sélecteur de mois pour le camembert
  private initMonthSelectOptions(): void {
    const now = new Date();
    const options: { label: string; value: string }[] = [];
    for (let i = 0; i < 12; i++) {
      const date = new Date(now.getFullYear(), now.getMonth() - i, 1);
      const value = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
      const label = date.toLocaleDateString('fr-BE', { year: 'numeric', month: 'long' });
      options.push({
        label: label.charAt(0).toUpperCase() + label.slice(1),
        value,
      });
    }
    this.monthSelectOptions = options;
  }

  // Initialise la liste des mois à charger selon la période sélectionnée
  private initMonths(): void {
    const now = new Date();
    const monthList: MonthData[] = [];

    for (let i = 0; i < this.selectedPeriod(); i++) {
      const date = new Date(now.getFullYear(), now.getMonth() - i, 1);
      const key = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
      const label = date.toLocaleDateString('fr-BE', { month: 'short' });
      const labelLong = date.toLocaleDateString('fr-BE', { year: 'numeric', month: 'long' });
      monthList.push({ key, label, labelLong, data: null, loading: false });
    }

    // Inverser pour ordre chronologique (du plus ancien au plus récent)
    this.months.set(monthList.reverse());
  }

  // Charge les catégories depuis l'API
  private async loadCategories(): Promise<void> {
    try {
      const response = await this.categoriesService.getAllCategories$Response();
      let categories = response.body;
      // Gestion de la réponse Blob (spécification OpenAPI ng-openapi-gen)
      if (categories instanceof Blob) {
        const text = await categories.text();
        categories = JSON.parse(text);
      }
      this.categories.set(Array.isArray(categories) ? categories : []);
    } catch (error) {
      console.error('Graphiques: erreur de chargement des catégories:', error);
    }
  }

  // Charge les données de tous les mois de la période
  async loadAllMonths(): Promise<void> {
    this.loading.set(true);
    try {
      await Promise.all(this.months().map((m) => this.loadMonthData(m.key)));
    } finally {
      this.loading.set(false);
    }
  }

  // Charge les données d'un mois spécifique depuis l'API dashboard
  private async loadMonthData(monthKey: string): Promise<void> {
    // Utiliser le cache si disponible
    if (this.dashboardCache.has(monthKey)) {
      this.months.update((months) =>
        months.map((m) =>
          m.key === monthKey ? { ...m, data: this.dashboardCache.get(monthKey)!, loading: false } : m
        )
      );
      return;
    }

    this.months.update((months) =>
      months.map((m) => (m.key === monthKey ? { ...m, loading: true } : m))
    );

    try {
      const response = await this.dashboardService.getMonthlyDashboard$Response({ month: monthKey });
      let data = response.body;
      // Gestion de la réponse Blob (spécification OpenAPI)
      if (data instanceof Blob) {
        const text = await data.text();
        data = JSON.parse(text);
      }
      this.dashboardCache.set(monthKey, data);
      this.months.update((months) =>
        months.map((m) => (m.key === monthKey ? { ...m, data, loading: false } : m))
      );
    } catch (error) {
      console.error(`Graphiques: erreur de chargement pour ${monthKey}:`, error);
      this.months.update((months) =>
        months.map((m) => (m.key === monthKey ? { ...m, loading: false } : m))
      );
    }
  }

  // Gère le changement de période (6 ou 12 mois)
  onPeriodChange(period: number): void {
    this.selectedPeriod.set(period);
    this.dashboardCache.clear();
    this.initMonths();
    this.loadAllMonths();
  }

  // Gère le changement de mois pour le camembert
  onMonthChange(month: string): void {
    this.selectedMonth.set(month);
    // Charger les données du mois sélectionné si pas encore en cache
    const exists = this.months().find((m) => m.key === month);
    if (!exists) {
      const date = new Date(month + '-01');
      const label = date.toLocaleDateString('fr-BE', { month: 'short' });
      const labelLong = date.toLocaleDateString('fr-BE', { year: 'numeric', month: 'long' });
      this.months.update((months) => [
        ...months,
        { key: month, label, labelLong, data: null, loading: false },
      ]);
    }
    this.loadMonthData(month);
  }

  // Retourne la classe CSS pour une valeur (vert si positif, rouge si négatif)
  getValueClass(value: number): string {
    if (value > 0) return 'text-success font-bold';
    if (value < 0) return 'text-danger font-bold';
    return 'text-text-muted dark:text-dark-text-muted';
  }
}
