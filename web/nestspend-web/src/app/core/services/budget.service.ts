import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { ApiConfiguration } from '../api/api-configuration';

// Clé localStorage pour la persistance des budgets en attendant un endpoint backend dédié
const BUDGETS_STORAGE_KEY = 'nestspend_budgets';

/**
 * Modèle d'un budget par catégorie.
 * Persisté en localStorage car l'endpoint /api/budgets n'existe pas encore côté backend.
 */
export interface BudgetModel {
  /** Identifiant unique du budget (UUID généré localement) */
  id: string;
  /** Identifiant de la catégorie associée */
  categoryId: string;
  /** Nom de la catégorie (dénormalisé pour l'affichage rapide) */
  categoryName: string;
  /** Couleur hexadécimale de la catégorie */
  categoryColor?: string;
  /** Icône PrimeNG de la catégorie */
  categoryIcon?: string;
  /** Limite mensuelle en euros */
  monthlyLimitEuros: number;
  /** Mois de référence au format YYYY-MM */
  month: string;
}

/**
 * Requête de création d'un budget (pour futur endpoint backend).
 */
export interface BudgetCreateRequest {
  categoryId: string;
  monthlyLimitCents: number;
  month: string;
}

/**
 * Requête de mise à jour d'un budget (pour futur endpoint backend).
 */
export interface BudgetUpdateRequest {
  monthlyLimitCents: number;
}

/**
 * Service Angular de gestion des budgets par catégorie.
 *
 * Actuellement basé sur le localStorage pour la persistance,
 * en attendant l'implémentation de l'endpoint /api/budgets côté backend.
 * La structure est prête pour migrer vers de vrais appels HTTP.
 */
@Injectable({ providedIn: 'root' })
export class BudgetService {
  // URL de base calculée depuis la configuration API (vide en production = chemin relatif via Nginx)
  private get baseUrl(): string {
    return this.apiConfig.rootUrl;
  }

  constructor(
    private http: HttpClient,
    private apiConfig: ApiConfiguration
  ) {}

  /**
   * Récupère tous les budgets depuis le localStorage.
   * À remplacer par un appel GET /api/budgets?month=YYYY-MM lorsque le backend le supportera.
   */
  getBudgets(month?: string): BudgetModel[] {
    const all = this.loadAllFromStorage();
    if (month) {
      return all.filter((b) => b.month === month);
    }
    return all;
  }

  /**
   * Crée un nouveau budget et le persiste dans le localStorage.
   * À remplacer par un appel POST /api/budgets lorsque le backend le supportera.
   */
  createBudget(budget: Omit<BudgetModel, 'id'>): BudgetModel {
    const newBudget: BudgetModel = {
      ...budget,
      id: crypto.randomUUID(),
    };
    const all = this.loadAllFromStorage();
    all.push(newBudget);
    this.saveAllToStorage(all);
    return newBudget;
  }

  /**
   * Met à jour un budget existant dans le localStorage.
   * À remplacer par un appel PUT /api/budgets/{id} lorsque le backend le supportera.
   */
  updateBudget(id: string, updates: Partial<BudgetModel>): BudgetModel | null {
    const all = this.loadAllFromStorage();
    const index = all.findIndex((b) => b.id === id);
    if (index === -1) return null;

    all[index] = { ...all[index], ...updates };
    this.saveAllToStorage(all);
    return all[index];
  }

  /**
   * Supprime un budget du localStorage par son identifiant.
   * À remplacer par un appel DELETE /api/budgets/{id} lorsque le backend le supportera.
   */
  deleteBudget(id: string): boolean {
    const all = this.loadAllFromStorage();
    const filtered = all.filter((b) => b.id !== id);
    if (filtered.length === all.length) return false;
    this.saveAllToStorage(filtered);
    return true;
  }

  /**
   * Copie les budgets d'un mois vers le mois suivant.
   * Ne copie pas les catégories déjà budgétées pour le mois cible.
   * Retourne le nombre de budgets copiés.
   */
  copyBudgetsToNextMonth(sourceMonth: string): number {
    const [year, month] = sourceMonth.split('-').map(Number);
    const nextDate = new Date(year, month, 1);
    const nextMonth = `${nextDate.getFullYear()}-${String(nextDate.getMonth() + 1).padStart(2, '0')}`;

    const all = this.loadAllFromStorage();
    const sourceBudgets = all.filter((b) => b.month === sourceMonth);
    const nextMonthCategoryIds = new Set(all.filter((b) => b.month === nextMonth).map((b) => b.categoryId));

    const budgetsToCopy = sourceBudgets
      .filter((b) => !nextMonthCategoryIds.has(b.categoryId))
      .map((b) => ({ ...b, id: crypto.randomUUID(), month: nextMonth }));

    if (budgetsToCopy.length > 0) {
      all.push(...budgetsToCopy);
      this.saveAllToStorage(all);
    }

    return budgetsToCopy.length;
  }

  /**
   * Charge tous les budgets depuis le localStorage.
   * Retourne un tableau vide en cas d'erreur de parsing.
   */
  private loadAllFromStorage(): BudgetModel[] {
    try {
      const raw = localStorage.getItem(BUDGETS_STORAGE_KEY);
      if (!raw) return [];
      const parsed = JSON.parse(raw);
      return Array.isArray(parsed) ? parsed : [];
    } catch (error) {
      console.error('BudgetService: erreur de lecture localStorage:', error);
      return [];
    }
  }

  /**
   * Sauvegarde tous les budgets dans le localStorage.
   */
  private saveAllToStorage(budgets: BudgetModel[]): void {
    try {
      localStorage.setItem(BUDGETS_STORAGE_KEY, JSON.stringify(budgets));
    } catch (error) {
      console.error('BudgetService: erreur de sauvegarde localStorage:', error);
    }
  }

  // ============================================================
  // Méthodes prêtes pour la migration vers le backend
  // (commentées jusqu'à l'implémentation de l'endpoint)
  // ============================================================

  /**
   * [FUTUR] Récupère les budgets depuis l'API backend.
   * Décommenter quand /api/budgets sera disponible.
   */
  // async getBudgetsFromApi(month: string): Promise<BudgetModel[]> {
  //   const params = new HttpParams().set('month', month);
  //   return firstValueFrom(this.http.get<BudgetModel[]>(`${this.baseUrl}/api/budgets`, { params }));
  // }

  /**
   * [FUTUR] Crée un budget via l'API backend.
   * Décommenter quand /api/budgets sera disponible.
   */
  // async createBudgetViaApi(request: BudgetCreateRequest): Promise<BudgetModel> {
  //   return firstValueFrom(this.http.post<BudgetModel>(`${this.baseUrl}/api/budgets`, request));
  // }

  /**
   * [FUTUR] Supprime un budget via l'API backend.
   * Décommenter quand /api/budgets sera disponible.
   */
  // async deleteBudgetViaApi(id: string): Promise<void> {
  //   return firstValueFrom(this.http.delete<void>(`${this.baseUrl}/api/budgets/${id}`));
  // }
}
