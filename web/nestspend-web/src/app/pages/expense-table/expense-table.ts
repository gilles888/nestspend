import { Component, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { CommonModule, CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, takeUntil } from 'rxjs';

import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TooltipModule } from 'primeng/tooltip';
import { ChartModule } from 'primeng/chart';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';

import { DashboardService } from '../../core/api/services/dashboard.service';
import { CategoriesService } from '../../core/api/services/categories.service';
import { DashboardResponse } from '../../core/api/models/dashboard-response';
import { CategoryExpenseResponse } from '../../core/api/models/category-expense-response';
import { CategoryResponse } from '../../core/api/models/category-response';

// Couleurs prédéfinies pour les catégories dans les graphiques
const CHART_COLORS = [
  '#3B82F6', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6',
  '#EC4899', '#06B6D4', '#84CC16', '#F97316', '#6366F1',
  '#14B8A6', '#D97706', '#DC2626', '#7C3AED', '#DB2777',
];

/**
 * Représente les données d'un mois dans le tableau de projection.
 */
interface MonthData {
  /** Clé au format YYYY-MM */
  key: string;
  /** Étiquette lisible ex: "Mars 2025" */
  label: string;
  /** Données du dashboard pour ce mois (null si non encore chargé) */
  data: DashboardResponse | null;
  /** Indique si le chargement est en cours */
  loading: boolean;
  /** Indique si une erreur s'est produite */
  error: boolean;
}

/**
 * Ligne du tableau de projection : données agrégées d'une catégorie sur tous les mois.
 */
interface ProjectionRow {
  /** Identifiant de la catégorie */
  categoryId: string;
  /** Nom de la catégorie */
  categoryName: string;
  /** Couleur de la catégorie */
  categoryColor?: string;
  /** Dépenses par mois en euros (clé = YYYY-MM) */
  monthlyAmounts: Record<string, number>;
  /** Total sur la période en euros */
  total: number;
  /** Moyenne mensuelle en euros */
  average: number;
}

@Component({
  selector: 'app-expense-table',
  standalone: true,
  imports: [
    CommonModule,
    CurrencyPipe,
    FormsModule,
    TranslateModule,
    TableModule,
    ButtonModule,
    SelectModule,
    SelectButtonModule,
    TooltipModule,
    ChartModule,
    ToastModule,
  ],
  providers: [MessageService],
  templateUrl: './expense-table.html',
  styleUrl: './expense-table.scss',
})
export class ExpenseTableComponent implements OnInit, OnDestroy {
  // Sujet de destruction pour éviter les fuites mémoire sur les subscriptions
  private readonly destroy$ = new Subject<void>();

  loading = signal(false);
  months = signal<MonthData[]>([]);
  categories = signal<CategoryResponse[]>([]);

  // Nombre de mois à afficher (6 ou 12)
  selectedPeriod = signal<number>(6);
  periodOptions = [
    { label: '6 mois', value: 6 },
    { label: '12 mois', value: 12 },
  ];

  // Vue sélectionnée : tableau ou graphique
  selectedView = signal<string>('table');
  viewOptions = [
    { label: 'Tableau', value: 'table', icon: 'pi pi-table' },
    { label: 'Graphique', value: 'chart', icon: 'pi pi-chart-bar' },
  ];

  // Cache des données du dashboard par mois pour éviter les rechargements
  private dashboardCache: Map<string, DashboardResponse> = new Map();

  // Lignes du tableau calculées à partir des données chargées
  projectionRows = computed((): ProjectionRow[] => {
    const months = this.months();
    const categories = this.categories();

    if (months.length === 0 || categories.length === 0) return [];

    // Construire un map categoryId -> nom/couleur
    const categoryMap = new Map<string, CategoryResponse>(categories.map((c) => [c.id ?? '', c]));

    // Agréger les dépenses par catégorie et par mois
    const rowMap = new Map<string, ProjectionRow>();

    for (const month of months) {
      if (!month.data?.expensesByCategory) continue;

      for (const expense of month.data.expensesByCategory) {
        if (!expense.categoryId) continue;

        const categoryInfo = categoryMap.get(expense.categoryId);
        const amountEuros = (expense.amountCents ?? 0) / 100;

        if (!rowMap.has(expense.categoryId)) {
          rowMap.set(expense.categoryId, {
            categoryId: expense.categoryId,
            categoryName: categoryInfo?.name ?? expense.categoryName ?? 'Inconnu',
            categoryColor: categoryInfo?.color,
            monthlyAmounts: {},
            total: 0,
            average: 0,
          });
        }

        const row = rowMap.get(expense.categoryId)!;
        row.monthlyAmounts[month.key] = amountEuros;
        row.total += amountEuros;
      }
    }

    // Calculer les moyennes et trier par total décroissant
    const loadedMonthsCount = months.filter((m) => m.data !== null).length || 1;
    const rows = Array.from(rowMap.values()).map((row) => ({
      ...row,
      average: row.total / loadedMonthsCount,
    }));

    return rows.sort((a, b) => b.total - a.total);
  });

  // Totaux par mois pour la ligne de total du tableau
  monthlyTotals = computed((): Record<string, number> => {
    const totals: Record<string, number> = {};
    for (const row of this.projectionRows()) {
      for (const [month, amount] of Object.entries(row.monthlyAmounts)) {
        totals[month] = (totals[month] ?? 0) + amount;
      }
    }
    return totals;
  });

  // Total global sur toute la période
  grandTotal = computed(() =>
    this.projectionRows().reduce((sum, row) => sum + row.total, 0)
  );

  // Moyenne mensuelle globale
  grandAverage = computed(() => {
    const rows = this.projectionRows();
    if (rows.length === 0) return 0;
    const loadedMonths = this.months().filter((m) => m.data !== null).length || 1;
    return this.grandTotal() / loadedMonths;
  });

  // Données du graphique en barres empilées par catégorie
  chartData = computed(() => {
    const rows = this.projectionRows();
    const months = this.months();

    if (rows.length === 0 || months.length === 0) return null;

    const labels = months.map((m) => m.label);

    return {
      labels,
      datasets: rows.slice(0, 10).map((row, index) => ({
        label: row.categoryName,
        data: months.map((m) => row.monthlyAmounts[m.key] ?? 0),
        backgroundColor: row.categoryColor || CHART_COLORS[index % CHART_COLORS.length],
        borderColor: row.categoryColor || CHART_COLORS[index % CHART_COLORS.length],
        borderWidth: 1,
      })),
    };
  });

  chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { position: 'right' as const },
      tooltip: {
        callbacks: {
          label: (context: { dataset: { label: string }; raw: number }) =>
            `${context.dataset.label}: €${context.raw.toFixed(2)}`,
        },
      },
    },
    scales: {
      x: { stacked: true },
      y: {
        stacked: true,
        beginAtZero: true,
        ticks: { callback: (value: number) => `€${value}` },
      },
    },
  };

  constructor(
    private dashboardService: DashboardService,
    private categoriesService: CategoriesService,
    private messageService: MessageService
  ) {}

  ngOnInit(): void {
    this.initMonths();
    this.loadCategories();
    this.loadAllMonthsData();
  }

  // Libère toutes les subscriptions lors de la destruction du composant
  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // Initialise la liste des mois à afficher selon la période sélectionnée
  private initMonths(): void {
    const now = new Date();
    const monthList: MonthData[] = [];

    // Générer les N derniers mois (du plus récent au plus ancien)
    for (let i = 0; i < this.selectedPeriod(); i++) {
      const date = new Date(now.getFullYear(), now.getMonth() - i, 1);
      const key = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
      const label = date.toLocaleDateString('default', { year: 'numeric', month: 'short' });
      monthList.push({ key, label, data: null, loading: false, error: false });
    }

    // Inverser pour afficher du plus ancien au plus récent
    this.months.set(monthList.reverse());
  }

  // Charge les catégories depuis l'API
  private async loadCategories(): Promise<void> {
    try {
      const response = await this.categoriesService.getAllCategories$Response();
      let categories = response.body;
      // Gestion de la réponse Blob (spécification OpenAPI)
      if (categories instanceof Blob) {
        const text = await categories.text();
        categories = JSON.parse(text);
      }
      this.categories.set(Array.isArray(categories) ? categories : []);
    } catch (error) {
      console.error('Erreur de chargement des catégories:', error);
    }
  }

  // Charge les données de tous les mois affichés
  async loadAllMonthsData(): Promise<void> {
    this.loading.set(true);

    try {
      const months = this.months();
      // Charger tous les mois en parallèle pour de meilleures performances
      await Promise.all(months.map((month) => this.loadMonthData(month.key)));
    } finally {
      this.loading.set(false);
    }
  }

  // Charge les données d'un mois spécifique
  private async loadMonthData(monthKey: string): Promise<void> {
    // Utiliser le cache si disponible
    if (this.dashboardCache.has(monthKey)) {
      this.updateMonthData(monthKey, this.dashboardCache.get(monthKey)!, false, false);
      return;
    }

    this.updateMonthLoadingState(monthKey, true);

    try {
      const response = await this.dashboardService.getMonthlyDashboard$Response({
        month: monthKey,
      });
      let data = response.body;
      // Gestion de la réponse Blob (spécification OpenAPI)
      if (data instanceof Blob) {
        const text = await data.text();
        data = JSON.parse(text);
      }
      this.dashboardCache.set(monthKey, data);
      this.updateMonthData(monthKey, data, false, false);
    } catch (error) {
      console.error(`Erreur de chargement pour le mois ${monthKey}:`, error);
      this.updateMonthLoadingState(monthKey, false, true);
    }
  }

  // Met à jour les données d'un mois dans le signal
  private updateMonthData(
    monthKey: string,
    data: DashboardResponse,
    loading: boolean,
    error: boolean
  ): void {
    this.months.update((months) =>
      months.map((m) => (m.key === monthKey ? { ...m, data, loading, error } : m))
    );
  }

  // Met à jour l'état de chargement d'un mois
  private updateMonthLoadingState(monthKey: string, loading: boolean, error = false): void {
    this.months.update((months) =>
      months.map((m) => (m.key === monthKey ? { ...m, loading, error } : m))
    );
  }

  // Gère le changement de période (6 ou 12 mois)
  onPeriodChange(period: number): void {
    this.selectedPeriod.set(period);
    this.dashboardCache.clear();
    this.initMonths();
    this.loadAllMonthsData();
  }

  // Retourne le montant d'une catégorie pour un mois donné (0 si non présent)
  getAmount(row: ProjectionRow, monthKey: string): number {
    return row.monthlyAmounts[monthKey] ?? 0;
  }

  // Retourne le total de toutes les dépenses d'un mois
  getMonthTotal(monthKey: string): number {
    return this.monthlyTotals()[monthKey] ?? 0;
  }

  // Indique si un mois est encore en cours de chargement
  isMonthLoading(monthKey: string): boolean {
    return this.months().find((m) => m.key === monthKey)?.loading ?? false;
  }

  // Indique si un mois est en erreur
  isMonthError(monthKey: string): boolean {
    return this.months().find((m) => m.key === monthKey)?.error ?? false;
  }

  // Exporte le tableau en CSV
  exportCsv(): void {
    const months = this.months();
    const rows = this.projectionRows();

    if (rows.length === 0) return;

    // En-têtes
    const headers = ['Catégorie', ...months.map((m) => m.label), 'Total', 'Moyenne'];
    const lines = [headers.join(';')];

    // Lignes de données
    for (const row of rows) {
      const values = [
        row.categoryName,
        ...months.map((m) => (row.monthlyAmounts[m.key] ?? 0).toFixed(2).replace('.', ',')),
        row.total.toFixed(2).replace('.', ','),
        row.average.toFixed(2).replace('.', ','),
      ];
      lines.push(values.join(';'));
    }

    // Ligne de total
    const totals = [
      'TOTAL',
      ...months.map((m) => this.getMonthTotal(m.key).toFixed(2).replace('.', ',')),
      this.grandTotal().toFixed(2).replace('.', ','),
      this.grandAverage().toFixed(2).replace('.', ','),
    ];
    lines.push(totals.join(';'));

    // Téléchargement du fichier
    const csvContent = '\uFEFF' + lines.join('\n'); // BOM UTF-8 pour Excel
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', `depenses-${this.selectedPeriod()}mois.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }
}
