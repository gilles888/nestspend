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
  SalaireEntry,
  FrequenceDepense,
} from '../../core/models/mois-type.model';
import { CategoriesService } from '../../core/api/services/categories.service';
import { CategoryResponse } from '../../core/api/models/category-response';

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

  /**
   * Catégories par défaut utilisées comme fallback si l'API est indisponible
   * ou retourne une liste vide.
   */
  private readonly categoriesParDefaut: { label: string; value: string }[] = [
    { label: 'Logement', value: 'Logement' },
    { label: 'Transport', value: 'Transport' },
    { label: 'Alimentation', value: 'Alimentation' },
    { label: 'Santé', value: 'Santé' },
    { label: 'Loisirs', value: 'Loisirs' },
    { label: 'Abonnements', value: 'Abonnements' },
    { label: 'Épargne', value: 'Épargne' },
    { label: 'Autre', value: 'Autre' },
  ];

  /** Options de catégories pour les sélecteurs — chargées depuis l'API au démarrage */
  categorieOptions = signal<{ label: string; value: string }[]>(this.categoriesParDefaut);

  /** Options de fréquence pour les sélecteurs */
  readonly frequenceOptions: { label: string; value: FrequenceDepense }[] = [
    { label: 'Mensuelle', value: 'MENSUELLE' },
    { label: 'Trimestrielle', value: 'TRIMESTRIELLE' },
    { label: 'Annuelle', value: 'ANNUELLE' },
  ];

  constructor(
    private moisTypeService: MoisTypeService,
    private messageService: MessageService,
    private translateService: TranslateService,
    private categoriesService: CategoriesService
  ) {}

  ngOnInit(): void {
    // Configurer la sauvegarde automatique avec debounce 800ms
    this.saveDebounce$
      .pipe(debounceTime(800), takeUntil(this.destroy$))
      .subscribe(() => this.sauvegarderSilencieusement());

    this.chargerMoisType();
    this.chargerCategories();
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
        // Migration : si le champ salaires est absent et revenus > 0, créer une ligne de salaire
        if (!local.salaires && (local.revenus ?? 0) > 0) {
          local.salaires = [{ id: 1, libelle: 'Salaire principal', montant: local.revenus }];
        } else if (!local.salaires) {
          // Initialiser avec une ligne vide pour les nouveaux mois types sans revenus migrés
          local.salaires = [{ id: Date.now(), libelle: 'Salaire principal', montant: 0 }];
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
        // Migration : si le champ salaires est absent et revenus > 0, créer une ligne de salaire
        if (!mt.salaires && (mt.revenus ?? 0) > 0) {
          mt.salaires = [{ id: 1, libelle: 'Salaire principal', montant: mt.revenus }];
        } else if (!mt.salaires) {
          mt.salaires = [{ id: Date.now(), libelle: 'Salaire principal', montant: 0 }];
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

  /**
   * Charge les catégories depuis l'API et met à jour le signal `categorieOptions`.
   * Si l'API est indisponible ou retourne une liste vide, utilise les catégories par défaut.
   */
  private async chargerCategories(): Promise<void> {
    try {
      const categories: CategoryResponse[] = await this.categoriesService.getAllCategories();
      if (categories && categories.length > 0) {
        // Transformer les réponses API en options de sélecteur
        const options = categories
          .filter((cat) => cat.name)
          .map((cat) => ({ label: cat.name!, value: cat.name! }));
        this.categorieOptions.set(options);
      } else {
        // Liste vide : fallback sur les catégories par défaut
        this.categorieOptions.set(this.categoriesParDefaut);
      }
    } catch (error) {
      // Erreur API : fallback silencieux sur les catégories par défaut
      console.warn('MoisType: impossible de charger les catégories depuis l\'API, utilisation des catégories par défaut:', error);
      this.categorieOptions.set(this.categoriesParDefaut);
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
      // Initialisation avec une ligne de salaire par défaut
      salaires: [{ id: Date.now(), libelle: 'Salaire principal', montant: 0 }],
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

  /**
   * Ajoute une nouvelle ligne de salaire vide dans la liste des salaires.
   * L'identifiant est basé sur Date.now() pour garantir l'unicité.
   */
  ajouterSalaire(): void {
    const mt = this.moisType();
    if (!mt) return;

    const nouvelleLigne: SalaireEntry = {
      id: Date.now(),
      libelle: '',
      montant: 0,
    };

    const updated: MoisType = {
      ...mt,
      salaires: [...(mt.salaires ?? []), nouvelleLigne],
    };
    this.moisType.set(updated);
    this.declencherSauvegarde();
  }

  /**
   * Supprime la ligne de salaire correspondant à l'identifiant donné.
   * Ne supprime pas si c'est la dernière ligne (au moins 1 ligne requise).
   */
  supprimerSalaire(id: number): void {
    const mt = this.moisType();
    if (!mt) return;

    const salairesActuels = mt.salaires ?? [];
    // Garantir au moins une ligne de salaire
    if (salairesActuels.length <= 1) return;

    const updated: MoisType = {
      ...mt,
      salaires: salairesActuels.filter((s) => s.id !== id),
    };
    this.moisType.set(updated);
    this.declencherSauvegarde();
  }

  /**
   * Appelé lors de la modification d'une ligne de salaire (libellé ou montant).
   * Déclenche la sauvegarde automatique.
   */
  onSalaireChange(): void {
    this.declencherSauvegarde();
  }

  /**
   * Calcule la somme totale des salaires + autres revenus pour l'affichage.
   * Utilisé dans le template pour afficher le total des revenus.
   */
  getTotalRevenus(): number {
    const mt = this.moisType();
    if (!mt) return 0;
    const sommeSalaires = (mt.salaires ?? []).reduce((sum, s) => sum + (s.montant ?? 0), 0);
    return sommeSalaires + (mt.autresRevenus ?? 0);
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

  /**
   * Retourne le libellé d'une catégorie en cherchant dans les options chargées dynamiquement.
   * Si la catégorie n'est pas trouvée, retourne la valeur brute.
   */
  getCategorieLabel(cat: string): string {
    return this.categorieOptions().find((o) => o.value === cat)?.label ?? cat;
  }

  /**
   * Retourne la classe CSS du badge de catégorie.
   * Couleur générique pour toutes les catégories dynamiques (chargées depuis l'API).
   */
  getCategorieBadgeClass(_cat: string): string {
    return 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300';
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
