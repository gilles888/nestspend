import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule, CurrencyPipe } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';

import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { ChartModule } from 'primeng/chart';
import { ButtonModule } from 'primeng/button';
import { SelectButtonModule } from 'primeng/selectbutton';

import { DashboardService } from '../../core/api/services/dashboard.service';
import { DashboardResponse } from '../../core/api/models/dashboard-response';
import { CategoryExpenseResponse } from '../../core/api/models/category-expense-response';

// Predefined colors for chart segments
const CHART_COLORS = [
  '#3B82F6', // blue
  '#10B981', // emerald
  '#F59E0B', // amber
  '#EF4444', // red
  '#8B5CF6', // violet
  '#EC4899', // pink
  '#06B6D4', // cyan
  '#84CC16', // lime
  '#F97316', // orange
  '#6366F1', // indigo
];

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    CurrencyPipe,
    TranslateModule,
    FormsModule,
    SelectModule,
    TableModule,
    ChartModule,
    ButtonModule,
    SelectButtonModule,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class DashboardComponent implements OnInit {
  loading = signal(false);
  dashboardData = signal<DashboardResponse | null>(null);
  expensesByCategory = signal<CategoryExpenseResponse[]>([]);
  errorMessage = signal<string | null>(null);

  selectedMonth = signal<string>(this.getCurrentMonth());
  monthOptions: { label: string; value: string }[] = [];

  // View toggle: 'table' or 'chart'
  selectedView = signal<string>('table');
  viewOptions = [
    { label: 'Table', value: 'table', icon: 'pi pi-table' },
    { label: 'Chart', value: 'chart', icon: 'pi pi-chart-pie' },
  ];

  // Chart data computed from expenses
  chartData = computed(() => {
    const expenses = this.expensesByCategory();
    if (!expenses || expenses.length === 0) {
      return null;
    }

    const labels = expenses.map((e) => e.categoryName ?? 'Unknown');
    const data = expenses.map((e) => (e.amountCents ?? 0) / 100);
    const colors = expenses.map((_, index) => CHART_COLORS[index % CHART_COLORS.length]);

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

  chartOptions = {
    plugins: {
      legend: {
        position: 'right',
        labels: {
          usePointStyle: true,
          padding: 20,
          font: {
            size: 12,
          },
        },
      },
      tooltip: {
        callbacks: {
          label: (context: { label: string; raw: number }) => {
            const value = context.raw;
            return `${context.label}: €${value.toFixed(2)}`;
          },
        },
      },
    },
    responsive: true,
    maintainAspectRatio: false,
    animation: {
      animateRotate: true,
      animateScale: true,
      duration: 800,
    },
  };

  constructor(
    private dashboardService: DashboardService,
    private translateService: TranslateService
  ) {
    this.initMonthOptions();
    this.initViewOptions();
  }

  private initViewOptions(): void {
    // Update labels with translations
    this.translateService.get(['dashboard.tableView', 'dashboard.chartView']).subscribe((translations) => {
      this.viewOptions = [
        { label: translations['dashboard.tableView'] || 'Table', value: 'table', icon: 'pi pi-table' },
        { label: translations['dashboard.chartView'] || 'Chart', value: 'chart', icon: 'pi pi-chart-pie' },
      ];
    });
  }

  ngOnInit(): void {
    this.loadDashboard();
  }

  private getCurrentMonth(): string {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
  }

  private initMonthOptions(): void {
    const now = new Date();
    const options: { label: string; value: string }[] = [];

    // Generate last 12 months
    for (let i = 0; i < 12; i++) {
      const date = new Date(now.getFullYear(), now.getMonth() - i, 1);
      const value = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
      const label = date.toLocaleDateString('default', { year: 'numeric', month: 'long' });
      options.push({ label, value });
    }

    this.monthOptions = options;
  }

  onMonthChange(month: string): void {
    this.selectedMonth.set(month);
    this.loadDashboard();
  }

  onViewChange(view: string): void {
    this.selectedView.set(view);
  }

  async loadDashboard(): Promise<void> {
    this.loading.set(true);
    this.errorMessage.set(null);

    try {
      const response = await this.dashboardService.getMonthlyDashboard$Response({
        month: this.selectedMonth(),
      });

      let data = response.body;
      // Handle Blob response from OpenAPI spec
      if (data instanceof Blob) {
        const text = await data.text();
        data = JSON.parse(text);
      }

      this.dashboardData.set(data);
      this.expensesByCategory.set(data.expensesByCategory ?? []);
    } catch (error) {
      this.errorMessage.set('dashboard.loadError');
      console.error('Dashboard load error:', error);
    } finally {
      this.loading.set(false);
    }
  }

  formatCentsToAmount(cents: number | undefined): number {
    return (cents ?? 0) / 100;
  }
}
