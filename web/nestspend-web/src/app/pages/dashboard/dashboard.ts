import { Component, OnInit, signal } from '@angular/core';
import { CommonModule, CurrencyPipe } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';

import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';

import { DashboardService } from '../../core/api/services/dashboard.service';
import { DashboardResponse } from '../../core/api/models/dashboard-response';
import { CategoryExpenseResponse } from '../../core/api/models/category-expense-response';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, CurrencyPipe, TranslateModule, FormsModule, SelectModule, TableModule],
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

  constructor(private dashboardService: DashboardService) {
    this.initMonthOptions();
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
      this.errorMessage.set('Failed to load dashboard data');
      console.error('Dashboard load error:', error);
    } finally {
      this.loading.set(false);
    }
  }

  formatCentsToAmount(cents: number | undefined): number {
    return (cents ?? 0) / 100;
  }
}
