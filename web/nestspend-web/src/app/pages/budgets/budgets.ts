import { Component, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { CommonModule, CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subject, takeUntil } from 'rxjs';

import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { SelectModule } from 'primeng/select';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { TagModule } from 'primeng/tag';
import { ChartModule } from 'primeng/chart';
import { ConfirmationService, MessageService } from 'primeng/api';

import { CategoriesService } from '../../core/api/services/categories.service';
import { DashboardService } from '../../core/api/services/dashboard.service';
import { CategoryResponse } from '../../core/api/models/category-response';
import { CategoryExpenseResponse } from '../../core/api/models/category-expense-response';

// Clé localStorage pour la persistance des budgets
const BUDGETS_STORAGE_KEY = 'nestspend_budgets';

/**
 * Modèle interne représentant un budget par catégorie.
 * Les budgets sont persistés dans localStorage (en attendant un endpoint backend dédié).
 */
export interface Budget {
  /** Identifiant unique du budget */
  id: string;
  /** Identifiant de la catégorie associée */
  categoryId: string;
  /** Nom de la catégorie (dénormalisé pour l'affichage) */
  categoryName: string;
  /** Couleur de la catégorie pour l'affichage */
  categoryColor?: string;
  /** Icône de la catégorie pour l'affichage */
  categoryIcon?: string;
  /** Montant maximum du budget mensuel en euros */
  monthlyLimitEuros: number;
  /** Mois de référence au format YYYY-MM */
  month: string;
}

/**
 * Budget enrichi avec les données de dépenses réelles pour le mois sélectionné.
 */
interface BudgetWithSpending extends Budget {
  /** Montant dépensé en euros pour ce mois */
  spentEuros: number;
  /** Pourcentage du budget utilisé (0-100+) */
  percentUsed: number;
  /** Montant restant en euros (peut être négatif si dépassement) */
  remainingEuros: number;
  /** Indique si le budget est dépassé */
  isOverBudget: boolean;
}

/**
 * Formulaire de création/édition de budget.
 */
interface BudgetForm {
  categoryId: string;
  monthlyLimitEuros: number;
}

@Component({
  selector: 'app-budgets',
  standalone: true,
  imports: [
    CommonModule,
    CurrencyPipe,
    FormsModule,
    TranslateModule,
    TableModule,
    ButtonModule,
    DialogModule,
    InputTextModule,
    InputNumberModule,
    SelectModule,
    ToastModule,
    TooltipModule,
    ConfirmDialogModule,
    TagModule,
    ChartModule,
  ],
  providers: [ConfirmationService, MessageService],
  templateUrl: './budgets.html',
  styleUrl: './budgets.scss',
})
export class BudgetsComponent implements OnInit, OnDestroy {
  // Sujet de destruction pour éviter les fuites mémoire sur les subscriptions
  private readonly destroy$ = new Subject<void>();

  loading = signal(false);
  budgets = signal<Budget[]>([]);
  categories = signal<CategoryResponse[]>([]);
  spendingByCategory = signal<CategoryExpenseResponse[]>([]);

  // Mois sélectionné pour la comparaison dépenses/budgets
  selectedMonth = signal<string>(this.getCurrentMonth());
  monthOptions: { label: string; value: string }[] = [];

  // Dialogue d'ajout/édition
  dialogVisible = signal(false);
  editingBudget = signal<Budget | null>(null);
  submitting = signal(false);

  budgetForm: BudgetForm = {
    categoryId: '',
    monthlyLimitEuros: 0,
  };

  // Budgets enrichis avec les données de dépenses réelles
  budgetsWithSpending = computed((): BudgetWithSpending[] => {
    const budgets = this.budgets();
    const spending = this.spendingByCategory();
    const month = this.selectedMonth();

    return budgets
      .filter((b) => b.month === month)
      .map((budget) => {
        const categorySpending = spending.find((s) => s.categoryId === budget.categoryId);
        const spentEuros = (categorySpending?.amountCents ?? 0) / 100;
        const percentUsed =
          budget.monthlyLimitEuros > 0
            ? Math.round((spentEuros / budget.monthlyLimitEuros) * 100)
            : 0;
        const remainingEuros = budget.monthlyLimitEuros - spentEuros;

        return {
          ...budget,
          spentEuros,
          percentUsed,
          remainingEuros,
          isOverBudget: spentEuros > budget.monthlyLimitEuros,
        };
      });
  });

  // Statistiques globales des budgets
  totalBudgetEuros = computed(() =>
    this.budgetsWithSpending().reduce((sum, b) => sum + b.monthlyLimitEuros, 0)
  );
  totalSpentEuros = computed(() =>
    this.budgetsWithSpending().reduce((sum, b) => sum + b.spentEuros, 0)
  );
  budgetsOverLimit = computed(() =>
    this.budgetsWithSpending().filter((b) => b.isOverBudget).length
  );

  // Données graphique comparaison budget vs dépenses réelles
  chartData = computed(() => {
    const budgets = this.budgetsWithSpending();
    if (budgets.length === 0) return null;

    return {
      labels: budgets.map((b) => b.categoryName),
      datasets: [
        {
          label: 'Budget',
          data: budgets.map((b) => b.monthlyLimitEuros),
          backgroundColor: 'rgba(59, 130, 246, 0.6)',
          borderColor: '#3B82F6',
          borderWidth: 1,
        },
        {
          label: 'Dépenses',
          data: budgets.map((b) => b.spentEuros),
          backgroundColor: budgets.map((b) =>
            b.isOverBudget ? 'rgba(239, 68, 68, 0.6)' : 'rgba(16, 185, 129, 0.6)'
          ),
          borderColor: budgets.map((b) => (b.isOverBudget ? '#EF4444' : '#10B981')),
          borderWidth: 1,
        },
      ],
    };
  });

  chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { position: 'top' as const },
      tooltip: {
        callbacks: {
          label: (context: { dataset: { label: string }; raw: number }) =>
            `${context.dataset.label}: €${context.raw.toFixed(2)}`,
        },
      },
    },
    scales: {
      y: {
        beginAtZero: true,
        ticks: {
          callback: (value: number) => `€${value}`,
        },
      },
    },
  };

  constructor(
    private categoriesService: CategoriesService,
    private dashboardService: DashboardService,
    private confirmationService: ConfirmationService,
    private messageService: MessageService,
    private translateService: TranslateService
  ) {
    this.initMonthOptions();
  }

  ngOnInit(): void {
    this.loadBudgetsFromStorage();
    this.loadData();
  }

  // Libère toutes les subscriptions lors de la destruction du composant
  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // Génère les options des 12 derniers mois pour le sélecteur
  private initMonthOptions(): void {
    const now = new Date();
    const options: { label: string; value: string }[] = [];

    for (let i = 0; i < 12; i++) {
      const date = new Date(now.getFullYear(), now.getMonth() - i, 1);
      const value = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
      const label = date.toLocaleDateString('default', { year: 'numeric', month: 'long' });
      options.push({ label, value });
    }

    this.monthOptions = options;
  }

  // Retourne le mois courant au format YYYY-MM
  private getCurrentMonth(): string {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
  }

  // Charge les catégories et les dépenses du mois sélectionné
  async loadData(): Promise<void> {
    this.loading.set(true);
    try {
      await Promise.all([this.loadCategories(), this.loadSpending()]);
    } finally {
      this.loading.set(false);
    }
  }

  // Charge la liste des catégories disponibles
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

  // Charge les dépenses réelles du mois sélectionné depuis l'API dashboard
  private async loadSpending(): Promise<void> {
    try {
      const response = await this.dashboardService.getMonthlyDashboard$Response({
        month: this.selectedMonth(),
      });
      let data = response.body;
      // Gestion de la réponse Blob (spécification OpenAPI)
      if (data instanceof Blob) {
        const text = await data.text();
        data = JSON.parse(text);
      }
      this.spendingByCategory.set(data?.expensesByCategory ?? []);
    } catch (error) {
      console.error('Erreur de chargement des dépenses:', error);
      this.spendingByCategory.set([]);
    }
  }

  // Charge les budgets depuis le localStorage
  private loadBudgetsFromStorage(): void {
    try {
      const raw = localStorage.getItem(BUDGETS_STORAGE_KEY);
      if (raw) {
        const parsed = JSON.parse(raw);
        this.budgets.set(Array.isArray(parsed) ? parsed : []);
      }
    } catch (error) {
      console.error('Erreur de chargement des budgets:', error);
      this.budgets.set([]);
    }
  }

  // Sauvegarde les budgets dans le localStorage
  private saveBudgetsToStorage(budgets: Budget[]): void {
    try {
      localStorage.setItem(BUDGETS_STORAGE_KEY, JSON.stringify(budgets));
    } catch (error) {
      console.error('Erreur de sauvegarde des budgets:', error);
    }
  }

  // Gestion du changement de mois sélectionné
  onMonthChange(month: string): void {
    this.selectedMonth.set(month);
    this.loadSpending();
  }

  // Catégories disponibles pour le formulaire (non déjà budgétées ce mois)
  getAvailableCategoryOptions(): { label: string; value: string }[] {
    const existingCategoryIds = this.budgets()
      .filter((b) => b.month === this.selectedMonth())
      .map((b) => b.categoryId);

    const editingId = this.editingBudget()?.categoryId;

    return this.categories()
      .filter(
        (c) =>
          c.id &&
          (!existingCategoryIds.includes(c.id) || c.id === editingId)
      )
      .map((c) => ({ label: c.name ?? '', value: c.id ?? '' }));
  }

  // Ouvre le dialogue de création d'un nouveau budget
  openNewBudgetDialog(): void {
    this.editingBudget.set(null);
    this.budgetForm = { categoryId: '', monthlyLimitEuros: 0 };
    this.dialogVisible.set(true);
  }

  // Ouvre le dialogue d'édition d'un budget existant
  openEditBudgetDialog(budget: Budget): void {
    this.editingBudget.set(budget);
    this.budgetForm = {
      categoryId: budget.categoryId,
      monthlyLimitEuros: budget.monthlyLimitEuros,
    };
    this.dialogVisible.set(true);
  }

  // Ferme le dialogue
  closeDialog(): void {
    this.dialogVisible.set(false);
    this.editingBudget.set(null);
    this.budgetForm = { categoryId: '', monthlyLimitEuros: 0 };
  }

  // Valide le formulaire de budget
  isFormValid(): boolean {
    return this.budgetForm.categoryId.length > 0 && this.budgetForm.monthlyLimitEuros > 0;
  }

  // Sauvegarde le budget (création ou mise à jour)
  saveBudget(): void {
    if (!this.isFormValid()) return;

    this.submitting.set(true);

    try {
      const category = this.categories().find((c) => c.id === this.budgetForm.categoryId);
      const currentBudgets = [...this.budgets()];
      const editing = this.editingBudget();

      if (editing) {
        // Mise à jour d'un budget existant
        const index = currentBudgets.findIndex((b) => b.id === editing.id);
        if (index !== -1) {
          currentBudgets[index] = {
            ...editing,
            categoryId: this.budgetForm.categoryId,
            categoryName: category?.name ?? '',
            categoryColor: category?.color ?? undefined,
            categoryIcon: category?.icon ?? undefined,
            monthlyLimitEuros: this.budgetForm.monthlyLimitEuros,
          };
        }
        // Message de succès pour la mise à jour
        this.translateService
          .get(['common.success', 'budgets.updateSuccess'])
          .pipe(takeUntil(this.destroy$))
          .subscribe((t) => {
            this.messageService.add({
              severity: 'success',
              summary: t['common.success'],
              detail: t['budgets.updateSuccess'],
            });
          });
      } else {
        // Création d'un nouveau budget
        const newBudget: Budget = {
          id: crypto.randomUUID(),
          categoryId: this.budgetForm.categoryId,
          categoryName: category?.name ?? '',
          categoryColor: category?.color ?? undefined,
          categoryIcon: category?.icon ?? undefined,
          monthlyLimitEuros: this.budgetForm.monthlyLimitEuros,
          month: this.selectedMonth(),
        };
        currentBudgets.push(newBudget);
        // Message de succès pour la création
        this.translateService
          .get(['common.success', 'budgets.createSuccess'])
          .pipe(takeUntil(this.destroy$))
          .subscribe((t) => {
            this.messageService.add({
              severity: 'success',
              summary: t['common.success'],
              detail: t['budgets.createSuccess'],
            });
          });
      }

      this.budgets.set(currentBudgets);
      this.saveBudgetsToStorage(currentBudgets);
      this.closeDialog();
    } finally {
      this.submitting.set(false);
    }
  }

  // Demande confirmation avant la suppression d'un budget
  confirmDelete(budget: Budget): void {
    this.translateService
      .get(['budgets.confirmDeleteHeader', 'budgets.confirmDeleteMessage'], {
        name: budget.categoryName,
      })
      .pipe(takeUntil(this.destroy$))
      .subscribe((t) => {
        this.confirmationService.confirm({
          header: t['budgets.confirmDeleteHeader'],
          message: t['budgets.confirmDeleteMessage'],
          icon: 'pi pi-exclamation-triangle',
          acceptButtonStyleClass: 'p-button-danger',
          accept: () => this.deleteBudget(budget),
        });
      });
  }

  // Supprime un budget
  private deleteBudget(budget: Budget): void {
    const updated = this.budgets().filter((b) => b.id !== budget.id);
    this.budgets.set(updated);
    this.saveBudgetsToStorage(updated);

    this.translateService
      .get(['common.success', 'budgets.deleteSuccess'])
      .pipe(takeUntil(this.destroy$))
      .subscribe((t) => {
        this.messageService.add({
          severity: 'success',
          summary: t['common.success'],
          detail: t['budgets.deleteSuccess'],
        });
      });
  }

  // Retourne le label du tag de statut en fonction du pourcentage utilisé
  getStatusLabel(budget: BudgetWithSpending): string {
    if (budget.isOverBudget) return this.translateService.instant('budgets.overBudget');
    if (budget.percentUsed >= 80) return this.translateService.instant('budgets.nearLimit');
    return this.translateService.instant('budgets.onTrack');
  }

  // Retourne la sévérité du tag de statut
  getStatusSeverity(budget: BudgetWithSpending): 'success' | 'warn' | 'danger' {
    if (budget.isOverBudget) return 'danger';
    if (budget.percentUsed >= 80) return 'warn';
    return 'success';
  }

  // Copie les budgets du mois courant vers le mois suivant
  copyBudgetsToNextMonth(): void {
    const [year, month] = this.selectedMonth().split('-').map(Number);
    const nextDate = new Date(year, month, 1); // Date du mois suivant
    const nextMonth = `${nextDate.getFullYear()}-${String(nextDate.getMonth() + 1).padStart(2, '0')}`;

    const currentMonthBudgets = this.budgets().filter((b) => b.month === this.selectedMonth());
    const nextMonthBudgets = this.budgets().filter((b) => b.month === nextMonth);

    if (currentMonthBudgets.length === 0) {
      this.translateService
        .get(['common.info', 'budgets.noBudgetsToCopy'])
        .pipe(takeUntil(this.destroy$))
        .subscribe((t) => {
          this.messageService.add({
            severity: 'info',
            summary: t['common.info'],
            detail: t['budgets.noBudgetsToCopy'],
          });
        });
      return;
    }

    // Copier uniquement les catégories non déjà budgétées le mois suivant
    const existingCategoryIds = new Set(nextMonthBudgets.map((b) => b.categoryId));
    const budgetsToCopy = currentMonthBudgets
      .filter((b) => !existingCategoryIds.has(b.categoryId))
      .map((b) => ({
        ...b,
        id: crypto.randomUUID(),
        month: nextMonth,
      }));

    if (budgetsToCopy.length === 0) {
      this.translateService
        .get(['common.info', 'budgets.budgetsAlreadyCopied'])
        .pipe(takeUntil(this.destroy$))
        .subscribe((t) => {
          this.messageService.add({
            severity: 'info',
            summary: t['common.info'],
            detail: t['budgets.budgetsAlreadyCopied'],
          });
        });
      return;
    }

    const updated = [...this.budgets(), ...budgetsToCopy];
    this.budgets.set(updated);
    this.saveBudgetsToStorage(updated);

    // Naviguer vers le mois suivant pour voir les budgets copiés
    this.selectedMonth.set(nextMonth);
    this.loadSpending();

    this.translateService
      .get(['common.success', 'budgets.copySuccess'], { count: budgetsToCopy.length })
      .pipe(takeUntil(this.destroy$))
      .subscribe((t) => {
        this.messageService.add({
          severity: 'success',
          summary: t['common.success'],
          detail: t['budgets.copySuccess'],
        });
      });
  }
}
