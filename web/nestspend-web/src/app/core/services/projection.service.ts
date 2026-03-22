import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { ApiConfiguration } from '../api/api-configuration';
import { DashboardService } from '../api/services/dashboard.service';
import { DashboardResponse } from '../api/models/dashboard-response';

// Clé localStorage pour la persistance des données saisies par l'utilisateur
const PROJECTION_OVERRIDES_KEY = 'nestspend_projection_overrides';

/**
 * Données saisies manuellement par l'utilisateur pour un mois donné.
 * Stockées en localStorage pour permettre la saisie des prévisions sans backend dédié.
 */
export interface MonthlyProjectionOverride {
  /** Mois au format YYYY-MM */
  month: string;
  /** Revenus prévus en euros (saisis manuellement) */
  plannedIncomeEuros: number;
  /** Dépenses fixes prévues en euros (loyer, abonnements, etc.) */
  plannedFixedExpensesEuros: number;
}

/**
 * Ligne complète du tableau de projection mensuelle.
 * Combine les données réelles (issues du backend) et les données prévisionnelles.
 */
export interface MonthlyProjectionRow {
  /** Mois au format YYYY-MM */
  month: string;
  /** Libellé du mois ex: "Mars 2026" */
  monthLabel: string;
  /** Revenus réels en euros (du dashboard) */
  actualIncomeEuros: number;
  /** Dépenses totales réelles en euros (du dashboard) */
  actualExpensesEuros: number;
  /** Revenus prévus saisis manuellement en euros */
  plannedIncomeEuros: number;
  /** Dépenses fixes prévues saisies manuellement en euros */
  plannedFixedExpensesEuros: number;
  /** Dépenses variables = dépenses réelles - dépenses fixes prévues */
  variableExpensesEuros: number;
  /** Épargne = revenus réels - dépenses totales réelles (si données disponibles) ou revenus prévus - dépenses totales */
  savingsEuros: number;
  /** Indique si les données réelles ont été chargées */
  hasRealData: boolean;
  /** Indique si le chargement est en cours */
  loading: boolean;
  /** Indique si une erreur s'est produite */
  error: boolean;
}

/**
 * Totaux annuels calculés à partir des lignes du tableau de projection.
 */
export interface AnnualProjectionTotals {
  totalActualIncomeEuros: number;
  totalActualExpensesEuros: number;
  totalPlannedIncomeEuros: number;
  totalPlannedFixedExpensesEuros: number;
  totalVariableExpensesEuros: number;
  totalSavingsEuros: number;
}

/**
 * Service Angular de gestion des projections de dépenses.
 *
 * Fournit :
 * - Le tableau de projection annuelle (Jan → Déc) avec données réelles + saisies
 * - La persistance des données saisies en localStorage
 * - Le calcul des totaux et de l'épargne
 * - L'export CSV du tableau
 *
 * Utilise DashboardService pour récupérer les données réelles par mois.
 */
@Injectable({ providedIn: 'root' })
export class ProjectionService {
  constructor(
    private http: HttpClient,
    private apiConfig: ApiConfiguration,
    private dashboardService: DashboardService
  ) {}

  /**
   * Génère les 12 lignes du tableau de projection pour une année donnée.
   * Initialise les données saisies depuis le localStorage.
   * @param year - Année à projeter (ex: 2026)
   */
  buildProjectionRows(year: number): MonthlyProjectionRow[] {
    const overrides = this.loadOverrides();
    const rows: MonthlyProjectionRow[] = [];

    for (let m = 0; m < 12; m++) {
      const date = new Date(year, m, 1);
      const monthKey = `${year}-${String(m + 1).padStart(2, '0')}`;
      const label = date.toLocaleDateString('fr-BE', { year: 'numeric', month: 'long' });
      // Capitaliser la première lettre du mois
      const monthLabel = label.charAt(0).toUpperCase() + label.slice(1);

      const override = overrides.find((o) => o.month === monthKey);

      rows.push({
        month: monthKey,
        monthLabel,
        actualIncomeEuros: 0,
        actualExpensesEuros: 0,
        plannedIncomeEuros: override?.plannedIncomeEuros ?? 0,
        plannedFixedExpensesEuros: override?.plannedFixedExpensesEuros ?? 0,
        variableExpensesEuros: 0,
        savingsEuros: 0,
        hasRealData: false,
        loading: false,
        error: false,
      });
    }

    return rows;
  }

  /**
   * Charge les données réelles d'un mois depuis le service dashboard.
   * Met à jour les calculs dérivés (épargne, dépenses variables).
   * @param row - Ligne de projection à enrichir
   * @returns Ligne mise à jour avec les données réelles
   */
  async loadRealDataForRow(row: MonthlyProjectionRow): Promise<MonthlyProjectionRow> {
    try {
      const response = await this.dashboardService.getMonthlyDashboard$Response({
        month: row.month,
      });

      let data = response.body;
      // Gestion de la réponse Blob (spécification OpenAPI ng-openapi-gen)
      if (data instanceof Blob) {
        const text = await data.text();
        data = JSON.parse(text) as DashboardResponse;
      }

      const actualIncomeEuros = (data?.totalIncomeCents ?? 0) / 100;
      // Note : le champ s'appelle totalExpenseCents (singulier) dans le modèle OpenAPI généré
      const actualExpensesEuros = (data?.totalExpenseCents ?? 0) / 100;

      return this.recalculate({
        ...row,
        actualIncomeEuros,
        actualExpensesEuros,
        hasRealData: true,
        loading: false,
        error: false,
      });
    } catch (error) {
      console.error(`ProjectionService: erreur de chargement pour ${row.month}:`, error);
      return { ...row, loading: false, error: true };
    }
  }

  /**
   * Recalcule les champs dérivés d'une ligne (épargne, dépenses variables).
   * Utilise les données réelles si disponibles, sinon les prévisions saisies.
   */
  recalculate(row: MonthlyProjectionRow): MonthlyProjectionRow {
    const incomeEuros = row.hasRealData ? row.actualIncomeEuros : row.plannedIncomeEuros;
    const expensesEuros = row.hasRealData
      ? row.actualExpensesEuros
      : row.plannedFixedExpensesEuros;

    const variableExpensesEuros = row.hasRealData
      ? Math.max(0, row.actualExpensesEuros - row.plannedFixedExpensesEuros)
      : 0;

    const totalExpenses = row.hasRealData
      ? row.actualExpensesEuros
      : row.plannedFixedExpensesEuros;

    const savingsEuros = incomeEuros - totalExpenses;

    return {
      ...row,
      variableExpensesEuros,
      savingsEuros,
    };
  }

  /**
   * Calcule les totaux annuels à partir des lignes de projection.
   */
  calculateAnnualTotals(rows: MonthlyProjectionRow[]): AnnualProjectionTotals {
    return {
      totalActualIncomeEuros: rows.reduce((sum, r) => sum + r.actualIncomeEuros, 0),
      totalActualExpensesEuros: rows.reduce((sum, r) => sum + r.actualExpensesEuros, 0),
      totalPlannedIncomeEuros: rows.reduce((sum, r) => sum + r.plannedIncomeEuros, 0),
      totalPlannedFixedExpensesEuros: rows.reduce(
        (sum, r) => sum + r.plannedFixedExpensesEuros,
        0
      ),
      totalVariableExpensesEuros: rows.reduce((sum, r) => sum + r.variableExpensesEuros, 0),
      totalSavingsEuros: rows.reduce((sum, r) => sum + r.savingsEuros, 0),
    };
  }

  /**
   * Sauvegarde les données saisies manuellement pour un mois donné.
   * Met à jour le localStorage et retourne la ligne recalculée.
   */
  saveOverride(
    row: MonthlyProjectionRow,
    plannedIncomeEuros: number,
    plannedFixedExpensesEuros: number
  ): MonthlyProjectionRow {
    const overrides = this.loadOverrides();
    const index = overrides.findIndex((o) => o.month === row.month);

    const override: MonthlyProjectionOverride = {
      month: row.month,
      plannedIncomeEuros,
      plannedFixedExpensesEuros,
    };

    if (index !== -1) {
      overrides[index] = override;
    } else {
      overrides.push(override);
    }

    this.saveOverrides(overrides);

    return this.recalculate({
      ...row,
      plannedIncomeEuros,
      plannedFixedExpensesEuros,
    });
  }

  /**
   * Exporte le tableau de projection en CSV (compatible Excel avec BOM UTF-8).
   * @param rows - Lignes du tableau
   * @param year - Année de la projection
   */
  exportToCsv(rows: MonthlyProjectionRow[], year: number): void {
    const headers = [
      'Mois',
      'Revenus (réels)',
      'Dépenses fixes (prévues)',
      'Dépenses variables',
      'Total dépenses (réelles)',
      'Épargne',
    ];

    const dataLines = rows.map((row) => [
      row.monthLabel,
      row.actualIncomeEuros.toFixed(2).replace('.', ','),
      row.plannedFixedExpensesEuros.toFixed(2).replace('.', ','),
      row.variableExpensesEuros.toFixed(2).replace('.', ','),
      row.actualExpensesEuros.toFixed(2).replace('.', ','),
      row.savingsEuros.toFixed(2).replace('.', ','),
    ]);

    const totals = this.calculateAnnualTotals(rows);
    const totalLine = [
      'TOTAL ANNUEL',
      totals.totalActualIncomeEuros.toFixed(2).replace('.', ','),
      totals.totalPlannedFixedExpensesEuros.toFixed(2).replace('.', ','),
      totals.totalVariableExpensesEuros.toFixed(2).replace('.', ','),
      totals.totalActualExpensesEuros.toFixed(2).replace('.', ','),
      totals.totalSavingsEuros.toFixed(2).replace('.', ','),
    ];

    const allLines = [headers.join(';'), ...dataLines.map((l) => l.join(';')), totalLine.join(';')];

    // BOM UTF-8 pour une compatibilité Excel optimale
    const csvContent = '\uFEFF' + allLines.join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', `projection-${year}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  /**
   * Charge les données de saisie depuis le localStorage.
   */
  private loadOverrides(): MonthlyProjectionOverride[] {
    try {
      const raw = localStorage.getItem(PROJECTION_OVERRIDES_KEY);
      if (!raw) return [];
      const parsed = JSON.parse(raw);
      return Array.isArray(parsed) ? parsed : [];
    } catch (error) {
      console.error('ProjectionService: erreur de lecture localStorage:', error);
      return [];
    }
  }

  /**
   * Sauvegarde les données de saisie dans le localStorage.
   */
  private saveOverrides(overrides: MonthlyProjectionOverride[]): void {
    try {
      localStorage.setItem(PROJECTION_OVERRIDES_KEY, JSON.stringify(overrides));
    } catch (error) {
      console.error('ProjectionService: erreur de sauvegarde localStorage:', error);
    }
  }
}
