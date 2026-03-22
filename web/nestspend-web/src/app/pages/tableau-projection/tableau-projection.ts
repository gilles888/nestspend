import { Component, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { CommonModule, CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { Subject } from 'rxjs';

import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { InputNumberModule } from 'primeng/inputnumber';
import { MessageService } from 'primeng/api';

import {
  ProjectionService,
  MonthlyProjectionRow,
  AnnualProjectionTotals,
} from '../../core/services/projection.service';

/**
 * Composant de tableau de projection annuelle des dépenses.
 *
 * Affiche un tableau style tableur avec les 12 mois de l'année sélectionnée.
 * Colonnes : Revenus / Dépenses fixes prévues / Dépenses variables / Total dépenses / Épargne
 * Les cellules "Revenus prévus" et "Dépenses fixes prévues" sont éditables et persistées en localStorage.
 * Les données réelles sont chargées depuis l'API dashboard.
 * Une ligne de total annuel est affichée en bas du tableau.
 */
@Component({
  selector: 'app-tableau-projection',
  standalone: true,
  imports: [
    CommonModule,
    CurrencyPipe,
    FormsModule,
    TranslateModule,
    ButtonModule,
    SelectModule,
    ToastModule,
    TooltipModule,
    InputNumberModule,
  ],
  providers: [MessageService],
  templateUrl: './tableau-projection.html',
  styleUrl: './tableau-projection.scss',
})
export class TableauProjectionComponent implements OnInit, OnDestroy {
  // Sujet de destruction pour éviter les fuites mémoire sur les subscriptions
  private readonly destroy$ = new Subject<void>();

  // Année courante sélectionnée pour la projection
  selectedYear = signal<number>(new Date().getFullYear());

  // Options des années disponibles (3 années passées + année courante + 1 future)
  yearOptions: { label: string; value: number }[] = [];

  // Lignes de projection générées par le service
  rows = signal<MonthlyProjectionRow[]>([]);

  // Totaux annuels calculés
  annualTotals = computed((): AnnualProjectionTotals =>
    this.projectionService.calculateAnnualTotals(this.rows())
  );

  // Indicateur de chargement global
  loading = signal(false);

  // Cellule en cours d'édition : identifiant du mois + champ édité
  editingCell = signal<{ month: string; field: 'income' | 'fixed' } | null>(null);

  // Valeurs temporaires en cours de saisie dans une cellule
  editingIncomeValue = 0;
  editingFixedValue = 0;

  constructor(
    private projectionService: ProjectionService,
    private messageService: MessageService
  ) {
    this.initYearOptions();
  }

  ngOnInit(): void {
    this.loadProjection();
  }

  // Libère toutes les subscriptions lors de la destruction du composant
  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // Génère les options des années disponibles autour de l'année courante
  private initYearOptions(): void {
    const currentYear = new Date().getFullYear();
    const options: { label: string; value: number }[] = [];

    // Afficher 3 années passées + année courante + 1 future
    for (let y = currentYear - 3; y <= currentYear + 1; y++) {
      options.push({ label: String(y), value: y });
    }
    this.yearOptions = options;
  }

  // Charge les lignes de projection pour l'année sélectionnée
  async loadProjection(): Promise<void> {
    this.loading.set(true);

    // Construire les 12 lignes depuis le service (avec overrides localStorage)
    const builtRows = this.projectionService.buildProjectionRows(this.selectedYear());
    this.rows.set(builtRows);

    // Charger les données réelles depuis l'API pour chaque mois (en parallèle)
    try {
      const enrichedRows = await Promise.all(
        builtRows.map((row) => this.projectionService.loadRealDataForRow(row))
      );
      this.rows.set(enrichedRows);
    } catch (error) {
      console.error('TableauProjection: erreur de chargement des données réelles:', error);
    } finally {
      this.loading.set(false);
    }
  }

  // Gère le changement d'année sélectionnée
  onYearChange(year: number): void {
    this.selectedYear.set(year);
    this.editingCell.set(null);
    this.loadProjection();
  }

  // Démarre l'édition de la cellule "Revenus prévus" pour un mois
  startEditIncome(row: MonthlyProjectionRow): void {
    this.editingCell.set({ month: row.month, field: 'income' });
    this.editingIncomeValue = row.plannedIncomeEuros;
  }

  // Démarre l'édition de la cellule "Dépenses fixes prévues" pour un mois
  startEditFixed(row: MonthlyProjectionRow): void {
    this.editingCell.set({ month: row.month, field: 'fixed' });
    this.editingFixedValue = row.plannedFixedExpensesEuros;
  }

  // Indique si une cellule Revenus est en cours d'édition pour un mois donné
  isEditingIncome(monthKey: string): boolean {
    const c = this.editingCell();
    return c?.month === monthKey && c?.field === 'income';
  }

  // Indique si une cellule Dépenses fixes est en cours d'édition pour un mois donné
  isEditingFixed(monthKey: string): boolean {
    const c = this.editingCell();
    return c?.month === monthKey && c?.field === 'fixed';
  }

  // Sauvegarde la valeur saisie et met à jour la ligne
  saveEdit(row: MonthlyProjectionRow): void {
    const cell = this.editingCell();
    if (!cell || cell.month !== row.month) return;

    // Récupérer les valeurs actuelles de la ligne
    const currentRow = this.rows().find((r) => r.month === row.month);
    if (!currentRow) return;

    const newIncome = cell.field === 'income' ? (this.editingIncomeValue ?? 0) : currentRow.plannedIncomeEuros;
    const newFixed = cell.field === 'fixed' ? (this.editingFixedValue ?? 0) : currentRow.plannedFixedExpensesEuros;

    // Sauvegarder dans le service (localStorage + recalcul)
    const updatedRow = this.projectionService.saveOverride(currentRow, newIncome, newFixed);

    // Mettre à jour le signal des lignes
    this.rows.update((rows) =>
      rows.map((r) => (r.month === row.month ? updatedRow : r))
    );

    this.editingCell.set(null);
  }

  // Annule l'édition en cours sans sauvegarder
  cancelEdit(): void {
    this.editingCell.set(null);
  }

  // Gère la touche Entrée pour valider la saisie
  onKeyDown(event: KeyboardEvent, row: MonthlyProjectionRow): void {
    if (event.key === 'Enter') {
      this.saveEdit(row);
    } else if (event.key === 'Escape') {
      this.cancelEdit();
    }
  }

  // Retourne la couleur CSS pour une valeur d'épargne (vert si positif, rouge si négatif)
  getSavingsClass(value: number): string {
    if (value > 0) return 'text-success font-semibold';
    if (value < 0) return 'text-danger font-semibold';
    return 'text-text-muted dark:text-dark-text-muted';
  }

  // Exporte le tableau en CSV via le service
  exportCsv(): void {
    this.projectionService.exportToCsv(this.rows(), this.selectedYear());
    this.messageService.add({
      severity: 'success',
      summary: 'Export',
      detail: `Projection ${this.selectedYear()} exportée en CSV`,
    });
  }

  // Calcule le taux d'épargne pour une ligne (en pourcentage)
  getSavingsRate(row: MonthlyProjectionRow): number {
    const income = row.hasRealData ? row.actualIncomeEuros : row.plannedIncomeEuros;
    if (income <= 0) return 0;
    return Math.round((row.savingsEuros / income) * 100);
  }

  // Taux d'épargne annuel global
  get annualSavingsRate(): number {
    const totals = this.annualTotals();
    const income = totals.totalActualIncomeEuros || totals.totalPlannedIncomeEuros;
    if (income <= 0) return 0;
    return Math.round((totals.totalSavingsEuros / income) * 100);
  }
}
