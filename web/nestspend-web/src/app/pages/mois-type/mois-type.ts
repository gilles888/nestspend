import {
  Component,
  OnInit,
  OnDestroy,
  signal,
  computed,
} from '@angular/core';
import { CommonModule, CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subject, debounceTime, takeUntil } from 'rxjs';

import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { SelectModule } from 'primeng/select';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { TagModule } from 'primeng/tag';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { ChartModule } from 'primeng/chart';
import { MessageService } from 'primeng/api';

import { MoisTypeService } from '../../core/services/mois-type.service';
import {
  MoisType,
  DepenseType,
  ResumeFinancier,
  ProjectionAnnuelle,
  CategorieDepense,
  FrequenceDepense,
} from '../../core/models/mois-type.model';

/** Noms des mois en français pour l'affichage */
const NOMS_MOIS = [
  'Jan', 'Fév', 'Mar', 'Avr', 'Mai', 'Jun',
  'Jul', 'Aoû', 'Sep', 'Oct', 'Nov', 'Déc',
];

/**
 * Page "Mon Mois Type".
 *
 * Permet de définir un mois de référence avec :
 * - Les revenus (salaire + autres)
 * - Les dépenses fixes (loyer, abonnements, assurances, etc.)
 * - Les dépenses variables (alimentation, loisirs, etc.)
 * - Un résumé financier temps réel (mensuel + annuel)
 * - Une projection annuelle avec graphique de l'épargne cumulée
 *
 * Les données sont persistées dans le localStorage avec sauvegarde automatique (debounce 800ms).
 */
@Component({
  selector: 'app-mois-type',
  standalone: true,
  imports: [
    CommonModule,
    CurrencyPipe,
    FormsModule,
    TranslateModule,
    ButtonModule,
    InputNumberModule,
    SelectModule,
    ToastModule,
    TooltipModule,
    TagModule,
    ToggleSwitchModule,
    ChartModule,
  ],
  providers: [MessageService],
  templateUrl: './mois-type.html',
  styleUrl: './mois-type.scss',
})
export class MoisTypeComponent implements OnInit, OnDestroy {
  /** Sujet de destruction pour les subscriptions RxJS */
  private readonly destroy$ = new Subject<void>();

  /** Sujet de debounce pour la sauvegarde automatique */
  private readonly saveDebounce$ = new Subject<void>();

  /** Indicateur de chargement initial */
  loading = signal(false);

  /** Indicateur de sauvegarde en cours */
  saving = signal(false);

  /** Mois type courant (null si pas encore créé) */
  moisType = signal<MoisType | null>(null);

  /** Indique si le mois type est en cours de création (premier lancement) */
  creatingNew = signal(false);

  /** Index de la ligne en cours d'édition (-1 si aucune) */
  editingFixeIndex = signal<number>(-1);
  editingVariableIndex = signal<number>(-1);

  /** Résumé financier calculé en temps réel */
  resume = computed((): ResumeFinancier => {
    const mt = this.moisType();
    if (!mt) {
      return {
        revenusMensuel: 0,
        depensesFixesMensuel: 0,
        depensesVariablesMensuel: 0,
        totalDepensesMensuel: 0,
        epargneMensuel: 0,
        tauxEpargneMensuel: 0,
        revenusAnnuel: 0,
        depensesFixesAnnuel: 0,
        depensesVariablesAnnuel: 0,
        totalDepensesAnnuel: 0,
        epargneAnnuel: 0,
        tauxEpargneAnnuel: 0,
      };
    }
    return this.moisTypeService.calculerResume(mt);
  });

  /** Projection annuelle calculée localement (fallback si API indisponible) */
  projectionLocale = computed((): ProjectionAnnuelle | null => {
    const mt = this.moisType();
    if (!mt) return null;
    return this.moisTypeService.genererProjectionLocale(mt, 2026);
  });

  /** Projection annuelle chargée depuis l'API (peut être null) */
  projectionApi = signal<ProjectionAnnuelle | null>(null);

  /** Projection active : API si disponible, locale sinon */
  projection = computed((): ProjectionAnnuelle | null => {
    return this.projectionApi() ?? this.projectionLocale();
  });

  /** Données du graphique Chart.js pour l'épargne cumulée */
  chartData = computed(() => {
    const proj = this.projection();
    if (!proj) return null;

    return {
      labels: NOMS_MOIS,
      datasets: [
        {
          label: 'Épargne mensuelle',
          data: proj.mois.map((m) => m.epargneMois),
          borderColor: '#10B981',
          backgroundColor: 'rgba(16, 185, 129, 0.15)',
          fill: false,
          tension: 0.4,
          yAxisID: 'y',
          pointRadius: 5,
          pointHoverRadius: 8,
          pointBackgroundColor: proj.mois.map((m) =>
            m.epargneMois >= 0 ? '#10B981' : '#EF4444'
          ),
        },
        {
          label: 'Épargne cumulée',
          data: proj.mois.map((m) => m.epargneCumulee),
          borderColor: '#4F46E5',
          backgroundColor: 'rgba(79, 70, 229, 0.15)',
          fill: true,
          tension: 0.4,
          yAxisID: 'y',
          pointRadius: 4,
          pointHoverRadius: 7,
          pointBackgroundColor: proj.mois.map((m) =>
            m.epargneCumulee >= 0 ? '#4F46E5' : '#EF4444'
          ),
        },
      ],
    };
  });

  /** Options Chart.js */
  chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: { intersect: false, mode: 'index' as const },
    plugins: {
      legend: { position: 'top' as const },
      tooltip: {
        callbacks: {
          label: (ctx: { dataset: { label: string }; raw: number }) =>
            `${ctx.dataset.label} : ${ctx.raw.toFixed(2)} €`,
        },
      },
    },
    scales: {
      y: {
        ticks: {
          callback: (v: number | string) => `${Number(v).toFixed(0)} €`,
        },
        grid: {
          color: (ctx: { tick: { value: number } }) =>
            ctx.tick.value === 0 ? 'rgba(0,0,0,0.3)' : 'rgba(0,0,0,0.08)',
          lineWidth: (ctx: { tick: { value: number } }) =>
            ctx.tick.value === 0 ? 2 : 1,
        },
      },
    },
  };

  /** Options de catégories pour les sélecteurs */
  readonly categorieOptions: { label: string; value: CategorieDepense }[] = [
    { label: 'Logement', value: 'LOGEMENT' },
    { label: 'Transport', value: 'TRANSPORT' },
    { label: 'Alimentation', value: 'ALIMENTATION' },
    { label: 'Santé', value: 'SANTE' },
    { label: 'Loisirs', value: 'LOISIRS' },
    { label: 'Abonnements', value: 'ABONNEMENTS' },
    { label: 'Épargne', value: 'EPARGNE' },
    { label: 'Autre', value: 'AUTRE' },
  ];

  /** Options de fréquence pour les sélecteurs */
  readonly frequenceOptions: { label: string; value: FrequenceDepense }[] = [
    { label: 'Mensuelle', value: 'MENSUELLE' },
    { label: 'Trimestrielle', value: 'TRIMESTRIELLE' },
    { label: 'Annuelle', value: 'ANNUELLE' },
  ];

  constructor(
    private moisTypeService: MoisTypeService,
    private messageService: MessageService,
    private translateService: TranslateService
  ) {}

  ngOnInit(): void {
    // Configurer la sauvegarde automatique avec debounce 800ms
    this.saveDebounce$
      .pipe(debounceTime(800), takeUntil(this.destroy$))
      .subscribe(() => this.sauvegarderSilencieusement());

    this.chargerMoisType();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ============================================================
  // Chargement et initialisation
  // ============================================================

  /** Charge le mois type depuis le localStorage ou l'API */
  async chargerMoisType(): Promise<void> {
    this.loading.set(true);
    try {
      // Essayer d'abord le localStorage
      const local = this.moisTypeService.loadFromStorage();
      if (local) {
        // Enrichir les dépenses avec le montant mensuel calculé
        if (local.depenses) {
          local.depenses = local.depenses.map((d) => ({
            ...d,
            montantMensuel: this.moisTypeService.calculerMontantMensuel(d),
          }));
        }
        this.moisType.set(local);
        // Essayer de charger la projection depuis l'API
        this.chargerProjectionApi();
        return;
      }

      // Sinon essayer l'API
      const apiList = await this.moisTypeService.getMoisTypes();
      if (apiList.length > 0) {
        const mt = apiList[0];
        if (mt.depenses) {
          mt.depenses = mt.depenses.map((d) => ({
            ...d,
            montantMensuel: this.moisTypeService.calculerMontantMensuel(d),
          }));
        }
        this.moisType.set(mt);
        this.moisTypeService.saveToStorage(mt);
        this.chargerProjectionApi();
      }
      // Si aucun mois type : rester à null pour afficher l'écran de création
    } catch (error) {
      console.error('MoisType: erreur de chargement:', error);
    } finally {
      this.loading.set(false);
    }
  }

  /** Charge la projection depuis l'API (en arrière-plan) */
  private async chargerProjectionApi(): Promise<void> {
    try {
      const proj = await this.moisTypeService.getProjection2026();
      if (proj) {
        this.projectionApi.set(proj);
      }
    } catch {
      // Silencieux : on utilise la projection locale comme fallback
    }
  }

  // ============================================================
  // Création du mois type
  // ============================================================

  /** Crée un nouveau mois type avec des valeurs par défaut */
  creerMoisType(): void {
    const nouveau: MoisType = {
      nom: 'Mon mois type 2026',
      annee: 2026,
      revenus: 0,
      autresRevenus: 0,
      depenses: [],
    };
    this.moisType.set(nouveau);
    this.creatingNew.set(false);
    this.moisTypeService.saveToStorage(nouveau);
    this.afficherSucces('moisType.createSuccess');
  }

  /** Charge les dépenses types d'exemple (fixes + variables) */
  chargerExemples(): void {
    const mt = this.moisType();
    if (!mt) return;

    const fixes = this.moisTypeService.getDepensesFixesExemple().map((d, i) => ({
      ...d,
      id: -(i + 1), // Identifiants temporaires négatifs
      montantMensuel: this.moisTypeService.calculerMontantMensuel(d as DepenseType),
    }));

    const variables = this.moisTypeService.getDepensesVariablesExemple().map((d, i) => ({
      ...d,
      id: -(fixes.length + i + 1),
      montantMensuel: this.moisTypeService.calculerMontantMensuel(d as DepenseType),
    }));

    const updated: MoisType = {
      ...mt,
      depenses: [...fixes, ...variables],
    };
    this.moisType.set(updated);
    this.declencherSauvegarde();
    this.afficherSucces('moisType.examplesLoaded');
  }

  // ============================================================
  // Gestion des revenus
  // ============================================================

  /** Appelé lors de la modification des revenus ou autres revenus */
  onRevenusChange(): void {
    this.declencherSauvegarde();
  }

  // ============================================================
  // Gestion des dépenses fixes
  // ============================================================

  /** Retourne uniquement les dépenses fixes */
  get depensesFixesList(): DepenseType[] {
    return (this.moisType()?.depenses ?? []).filter((d) => d.typeDepense === 'FIXE');
  }

  /** Retourne uniquement les dépenses variables */
  get depensesVariablesList(): DepenseType[] {
    return (this.moisType()?.depenses ?? []).filter((d) => d.typeDepense === 'VARIABLE');
  }

  /** Ajoute une nouvelle dépense fixe vide */
  ajouterDepenseFixe(): void {
    const mt = this.moisType();
    if (!mt) return;

    const nouvelle: DepenseType = {
      id: -Date.now(), // Identifiant temporaire
      nom: '',
      montant: 0,
      categorie: 'AUTRE',
      typeDepense: 'FIXE',
      frequence: 'MENSUELLE',
      actif: true,
      montantMensuel: 0,
    };

    const updated = { ...mt, depenses: [...(mt.depenses ?? []), nouvelle] };
    this.moisType.set(updated);

    // Mettre en mode édition sur la nouvelle ligne
    const indexFixe = this.depensesFixesList.length - 1;
    this.editingFixeIndex.set(indexFixe);
  }

  /** Ajoute une nouvelle dépense variable vide */
  ajouterDepenseVariable(): void {
    const mt = this.moisType();
    if (!mt) return;

    const nouvelle: DepenseType = {
      id: -Date.now(),
      nom: '',
      montant: 0,
      categorie: 'AUTRE',
      typeDepense: 'VARIABLE',
      frequence: 'MENSUELLE',
      actif: true,
      montantMensuel: 0,
    };

    const updated = { ...mt, depenses: [...(mt.depenses ?? []), nouvelle] };
    this.moisType.set(updated);
    const indexVariable = this.depensesVariablesList.length - 1;
    this.editingVariableIndex.set(indexVariable);
  }

  /** Supprime une dépense par son identifiant temporaire ou réel */
  supprimerDepense(depense: DepenseType): void {
    const mt = this.moisType();
    if (!mt) return;

    const updated = {
      ...mt,
      depenses: (mt.depenses ?? []).filter((d) => d.id !== depense.id),
    };
    this.moisType.set(updated);
    this.declencherSauvegarde();
  }

  /** Mise à jour d'une dépense (recalcule montantMensuel et déclenche sauvegarde) */
  onDepenseChange(depense: DepenseType): void {
    const mt = this.moisType();
    if (!mt) return;

    // Recalculer le montant mensuel
    depense.montantMensuel = this.moisTypeService.calculerMontantMensuel(depense);

    // Forcer la mise à jour du signal en recréant le tableau
    const updated = {
      ...mt,
      depenses: (mt.depenses ?? []).map((d) =>
        d.id === depense.id ? { ...depense } : d
      ),
    };
    this.moisType.set(updated);
    this.declencherSauvegarde();
  }

  /** Toggle l'état actif d'une dépense */
  toggleDepense(depense: DepenseType): void {
    depense.actif = !depense.actif;
    this.onDepenseChange(depense);
  }

  // ============================================================
  // Helpers d'affichage
  // ============================================================

  /** Retourne le libellé d'une catégorie */
  getCategorieLabel(cat: CategorieDepense): string {
    return this.categorieOptions.find((o) => o.value === cat)?.label ?? cat;
  }

  /** Retourne la couleur CSS du badge de catégorie */
  getCategorieBadgeClass(cat: CategorieDepense): string {
    const map: Record<CategorieDepense, string> = {
      LOGEMENT: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
      TRANSPORT: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300',
      ALIMENTATION: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
      SANTE: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300',
      LOISIRS: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300',
      ABONNEMENTS: 'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-300',
      EPARGNE: 'bg-teal-100 text-teal-700 dark:bg-teal-900/30 dark:text-teal-300',
      AUTRE: 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300',
    };
    return map[cat] ?? map['AUTRE'];
  }

  /** Retourne la classe CSS pour la valeur d'épargne (vert/rouge) */
  getEpargneClass(valeur: number): string {
    if (valeur > 0) return 'text-success font-bold';
    if (valeur < 0) return 'text-danger font-bold';
    return 'text-text-muted dark:text-dark-text-muted';
  }

  /** Retourne le libellé court d'une fréquence */
  getFrequenceLabel(f: FrequenceDepense): string {
    const map: Record<FrequenceDepense, string> = {
      MENSUELLE: '/mois',
      TRIMESTRIELLE: '/trim.',
      ANNUELLE: '/an',
    };
    return map[f] ?? f;
  }

  /** Retourne true si l'épargne est positive (pour stylisation) */
  isEpargnePositive(valeur: number): boolean {
    return valeur > 0;
  }

  // ============================================================
  // Sauvegarde
  // ============================================================

  /** Déclenche la sauvegarde automatique avec debounce */
  declencherSauvegarde(): void {
    this.saveDebounce$.next();
  }

  /** Sauvegarde silencieuse dans le localStorage sans toast */
  private sauvegarderSilencieusement(): void {
    const mt = this.moisType();
    if (!mt) return;
    this.moisTypeService.saveToStorage(mt);
  }

  // ============================================================
  // Notifications
  // ============================================================

  private afficherSucces(cle: string): void {
    this.translateService
      .get(['common.success', cle])
      .pipe(takeUntil(this.destroy$))
      .subscribe((t) => {
        this.messageService.add({
          severity: 'success',
          summary: t['common.success'],
          detail: t[cle],
          life: 3000,
        });
      });
  }

  private afficherErreur(message: string): void {
    this.messageService.add({
      severity: 'error',
      summary: 'Erreur',
      detail: message,
      life: 4000,
    });
  }
}
