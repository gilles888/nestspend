import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom, Subject } from 'rxjs';
import { ApiConfiguration } from '../api/api-configuration';
import {
  MoisType,
  DepenseType,
  ProjectionAnnuelle,
  ResumeFinancier,
  FrequenceDepense,
  TypeDepense,
} from '../models/mois-type.model';

/** Clé localStorage pour la persistance du mois type en attendant un endpoint backend */
const MOIS_TYPE_STORAGE_KEY = 'nestspend_mois_type';

/**
 * Service Angular de gestion du "Mois Type".
 *
 * Gère les appels HTTP vers :
 * - /api/mois-type (CRUD des mois types)
 * - /api/depenses-type (CRUD des dépenses types)
 * - /api/projections/2026 (projection annuelle)
 * - /api/projections/2026/epargne (données d'épargne)
 *
 * Fournit également :
 * - La persistance locale (localStorage) comme fallback
 * - Les calculs de résumé financier en temps réel
 * - La génération des dépenses types d'exemple
 */
@Injectable({ providedIn: 'root' })
export class MoisTypeService {
  /** URL de base de l'API */
  private get baseUrl(): string {
    return this.apiConfig.rootUrl;
  }

  /** Sujet notifiant les composants qu'une mise à jour a eu lieu */
  private readonly updated$ = new Subject<void>();

  constructor(
    private http: HttpClient,
    private apiConfig: ApiConfiguration
  ) {}

  // ============================================================
  // CRUD Mois Type via API REST
  // ============================================================

  /**
   * Récupère tous les mois types depuis l'API.
   */
  async getMoisTypes(): Promise<MoisType[]> {
    try {
      return await firstValueFrom(
        this.http.get<MoisType[]>(`${this.baseUrl}/api/mois-type`)
      );
    } catch (error) {
      console.warn('MoisTypeService: API indisponible, retour localStorage:', error);
      return this.loadAllFromStorage();
    }
  }

  /**
   * Récupère un mois type par son identifiant.
   */
  async getMoisType(id: number): Promise<MoisType | null> {
    try {
      return await firstValueFrom(
        this.http.get<MoisType>(`${this.baseUrl}/api/mois-type/${id}`)
      );
    } catch (error) {
      console.warn('MoisTypeService: API indisponible pour getMoisType:', error);
      const all = this.loadAllFromStorage();
      return all.find((m) => m.id === id) ?? null;
    }
  }

  /**
   * Crée un nouveau mois type via l'API.
   * Fallback sur localStorage si l'API est indisponible.
   */
  async createMoisType(data: Omit<MoisType, 'id'>): Promise<MoisType> {
    try {
      const result = await firstValueFrom(
        this.http.post<MoisType>(`${this.baseUrl}/api/mois-type`, data)
      );
      return result;
    } catch (error) {
      console.warn('MoisTypeService: API indisponible, création locale:', error);
      // Persistance locale avec id généré
      const newMoisType: MoisType = {
        ...data,
        id: Date.now(),
      };
      const all = this.loadAllFromStorage();
      all.push(newMoisType);
      this.saveAllToStorage(all);
      return newMoisType;
    }
  }

  /**
   * Met à jour un mois type existant via l'API.
   */
  async updateMoisType(id: number, data: Partial<MoisType>): Promise<MoisType> {
    try {
      return await firstValueFrom(
        this.http.put<MoisType>(`${this.baseUrl}/api/mois-type/${id}`, data)
      );
    } catch (error) {
      console.warn('MoisTypeService: API indisponible, mise à jour locale:', error);
      const all = this.loadAllFromStorage();
      const index = all.findIndex((m) => m.id === id);
      if (index !== -1) {
        all[index] = { ...all[index], ...data };
        this.saveAllToStorage(all);
        return all[index];
      }
      throw new Error(`MoisType avec l'id ${id} introuvable`);
    }
  }

  /**
   * Supprime un mois type via l'API.
   */
  async deleteMoisType(id: number): Promise<void> {
    try {
      await firstValueFrom(
        this.http.delete<void>(`${this.baseUrl}/api/mois-type/${id}`)
      );
    } catch (error) {
      console.warn('MoisTypeService: API indisponible, suppression locale:', error);
      const all = this.loadAllFromStorage();
      const filtered = all.filter((m) => m.id !== id);
      this.saveAllToStorage(filtered);
    }
  }

  // ============================================================
  // CRUD Dépenses Type via API REST
  // ============================================================

  /**
   * Récupère les dépenses types, éventuellement filtrées par mois type.
   */
  async getDepensesType(moisTypeId?: number): Promise<DepenseType[]> {
    try {
      let params = new HttpParams();
      if (moisTypeId !== undefined) {
        params = params.set('moisTypeId', String(moisTypeId));
      }
      return await firstValueFrom(
        this.http.get<DepenseType[]>(`${this.baseUrl}/api/depenses-type`, { params })
      );
    } catch (error) {
      console.warn('MoisTypeService: API indisponible pour getDepensesType:', error);
      return [];
    }
  }

  /**
   * Crée une nouvelle dépense type via l'API.
   */
  async createDepenseType(data: Omit<DepenseType, 'id'>): Promise<DepenseType> {
    return firstValueFrom(
      this.http.post<DepenseType>(`${this.baseUrl}/api/depenses-type`, data)
    );
  }

  /**
   * Met à jour une dépense type existante via l'API.
   */
  async updateDepenseType(id: number, data: Partial<DepenseType>): Promise<DepenseType> {
    return firstValueFrom(
      this.http.put<DepenseType>(`${this.baseUrl}/api/depenses-type/${id}`, data)
    );
  }

  /**
   * Supprime une dépense type via l'API.
   */
  async deleteDepenseType(id: number): Promise<void> {
    return firstValueFrom(
      this.http.delete<void>(`${this.baseUrl}/api/depenses-type/${id}`)
    );
  }

  /**
   * Active ou désactive une dépense type via l'API (PATCH toggle).
   */
  async toggleDepenseType(id: number): Promise<DepenseType> {
    return firstValueFrom(
      this.http.patch<DepenseType>(`${this.baseUrl}/api/depenses-type/${id}/toggle`, {})
    );
  }

  // ============================================================
  // Projections via API REST
  // ============================================================

  /**
   * Récupère la projection annuelle 2026 depuis l'API.
   */
  async getProjection2026(): Promise<ProjectionAnnuelle | null> {
    try {
      return await firstValueFrom(
        this.http.get<ProjectionAnnuelle>(`${this.baseUrl}/api/projections/2026`)
      );
    } catch (error) {
      console.warn('MoisTypeService: API indisponible pour getProjection2026:', error);
      return null;
    }
  }

  /**
   * Récupère les données d'épargne 2026 depuis l'API.
   */
  async getEpargne2026(): Promise<unknown> {
    try {
      return await firstValueFrom(
        this.http.get<unknown>(`${this.baseUrl}/api/projections/2026/epargne`)
      );
    } catch (error) {
      console.warn('MoisTypeService: API indisponible pour getEpargne2026:', error);
      return null;
    }
  }

  // ============================================================
  // Calculs côté client
  // ============================================================

  /**
   * Calcule le montant mensuel d'une dépense selon sa fréquence.
   * - MENSUELLE : montant tel quel
   * - TRIMESTRIELLE : montant / 3
   * - ANNUELLE : montant / 12
   */
  calculerMontantMensuel(depense: DepenseType): number {
    if (!depense.actif) return 0;
    switch (depense.frequence) {
      case 'MENSUELLE':
        return depense.montant;
      case 'TRIMESTRIELLE':
        return depense.montant / 3;
      case 'ANNUELLE':
        return depense.montant / 12;
      default:
        return depense.montant;
    }
  }

  /**
   * Calcule le résumé financier en temps réel à partir d'un mois type.
   * Prend en compte uniquement les dépenses actives.
   */
  calculerResume(moisType: MoisType): ResumeFinancier {
    const depenses = moisType.depenses ?? [];

    // Revenus totaux
    const revenusMensuel = (moisType.revenus ?? 0) + (moisType.autresRevenus ?? 0);

    // Dépenses fixes actives ramenées au mois
    const depensesFixesMensuel = depenses
      .filter((d) => d.actif && d.typeDepense === 'FIXE')
      .reduce((sum, d) => sum + this.calculerMontantMensuel(d), 0);

    // Dépenses variables actives ramenées au mois
    const depensesVariablesMensuel = depenses
      .filter((d) => d.actif && d.typeDepense === 'VARIABLE')
      .reduce((sum, d) => sum + this.calculerMontantMensuel(d), 0);

    const totalDepensesMensuel = depensesFixesMensuel + depensesVariablesMensuel;
    const epargneMensuel = revenusMensuel - totalDepensesMensuel;
    const tauxEpargneMensuel =
      revenusMensuel > 0 ? Math.round((epargneMensuel / revenusMensuel) * 100) : 0;

    // Projections annuelles (x12)
    const revenusAnnuel = revenusMensuel * 12;
    const depensesFixesAnnuel = depensesFixesMensuel * 12;
    const depensesVariablesAnnuel = depensesVariablesMensuel * 12;
    const totalDepensesAnnuel = totalDepensesMensuel * 12;
    const epargneAnnuel = epargneMensuel * 12;
    const tauxEpargneAnnuel =
      revenusAnnuel > 0 ? Math.round((epargneAnnuel / revenusAnnuel) * 100) : 0;

    return {
      revenusMensuel,
      depensesFixesMensuel,
      depensesVariablesMensuel,
      totalDepensesMensuel,
      epargneMensuel,
      tauxEpargneMensuel,
      revenusAnnuel,
      depensesFixesAnnuel,
      depensesVariablesAnnuel,
      totalDepensesAnnuel,
      epargneAnnuel,
      tauxEpargneAnnuel,
    };
  }

  /**
   * Génère une projection annuelle locale à partir d'un mois type.
   * Utilisé quand l'API de projection n'est pas disponible.
   * Prend en compte les fréquences (trimestrielle, annuelle) pour répartir les dépenses.
   */
  genererProjectionLocale(moisType: MoisType, annee: number): ProjectionAnnuelle {
    const depenses = moisType.depenses ?? [];
    const revenusMensuel = (moisType.revenus ?? 0) + (moisType.autresRevenus ?? 0);

    const NOMS_MOIS = [
      'Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin',
      'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre',
    ];

    let epargneCumulee = 0;
    const moisProjection = NOMS_MOIS.map((nomMois, i) => {
      const numeroMois = i + 1; // 1-12

      // Dépenses fixes : MENSUELLE toujours, TRIMESTRIELLE en mois 1/4/7/10, ANNUELLE en mois 1
      const depensesFixes = depenses
        .filter((d) => d.actif && d.typeDepense === 'FIXE')
        .reduce((sum, d) => {
          if (d.frequence === 'MENSUELLE') return sum + d.montant;
          if (d.frequence === 'TRIMESTRIELLE' && numeroMois % 3 === 1) return sum + d.montant;
          if (d.frequence === 'ANNUELLE' && numeroMois === 1) return sum + d.montant;
          return sum;
        }, 0);

      // Dépenses variables : MENSUELLE toujours, TRIMESTRIELLE en mois 1/4/7/10, ANNUELLE en mois 1
      const depensesVariables = depenses
        .filter((d) => d.actif && d.typeDepense === 'VARIABLE')
        .reduce((sum, d) => {
          if (d.frequence === 'MENSUELLE') return sum + d.montant;
          if (d.frequence === 'TRIMESTRIELLE' && numeroMois % 3 === 1) return sum + d.montant;
          if (d.frequence === 'ANNUELLE' && numeroMois === 1) return sum + d.montant;
          return sum;
        }, 0);

      const totalDepenses = depensesFixes + depensesVariables;
      const epargneMois = revenusMensuel - totalDepenses;
      epargneCumulee += epargneMois;

      return {
        mois: numeroMois,
        nomMois,
        revenus: revenusMensuel,
        depensesFixes,
        depensesVariables,
        totalDepenses,
        epargneMois,
        epargneCumulee,
      };
    });

    const totalRevenus = moisProjection.reduce((s, m) => s + m.revenus, 0);
    const totalDepenses = moisProjection.reduce((s, m) => s + m.totalDepenses, 0);
    const totalEpargne = moisProjection.reduce((s, m) => s + m.epargneMois, 0);
    const tauxEpargne = totalRevenus > 0 ? Math.round((totalEpargne / totalRevenus) * 100) : 0;

    return {
      annee,
      mois: moisProjection,
      totalRevenus,
      totalDepenses,
      totalEpargne,
      tauxEpargne,
    };
  }

  // ============================================================
  // Données d'exemple
  // ============================================================

  /**
   * Retourne la liste des dépenses fixes types prédéfinies pour démarrer rapidement.
   */
  getDepensesFixesExemple(): Omit<DepenseType, 'id'>[] {
    return [
      { nom: 'Loyer / Remboursement hypothèque', montant: 900, categorie: 'LOGEMENT', typeDepense: 'FIXE', frequence: 'MENSUELLE', actif: true },
      { nom: 'Électricité / Gaz', montant: 120, categorie: 'LOGEMENT', typeDepense: 'FIXE', frequence: 'MENSUELLE', actif: true },
      { nom: 'Eau', montant: 30, categorie: 'LOGEMENT', typeDepense: 'FIXE', frequence: 'MENSUELLE', actif: true },
      { nom: 'Internet', montant: 40, categorie: 'ABONNEMENTS', typeDepense: 'FIXE', frequence: 'MENSUELLE', actif: true },
      { nom: 'GSM', montant: 20, categorie: 'ABONNEMENTS', typeDepense: 'FIXE', frequence: 'MENSUELLE', actif: true },
      { nom: 'Assurance voiture', montant: 80, categorie: 'TRANSPORT', typeDepense: 'FIXE', frequence: 'MENSUELLE', actif: true },
      { nom: 'Assurance habitation', montant: 30, categorie: 'LOGEMENT', typeDepense: 'FIXE', frequence: 'MENSUELLE', actif: true },
      { nom: 'Assurance vie', montant: 50, categorie: 'AUTRE', typeDepense: 'FIXE', frequence: 'MENSUELLE', actif: true },
      { nom: 'Mutuelle complémentaire', montant: 50, categorie: 'SANTE', typeDepense: 'FIXE', frequence: 'MENSUELLE', actif: true },
      { nom: 'Crédit voiture', montant: 200, categorie: 'TRANSPORT', typeDepense: 'FIXE', frequence: 'MENSUELLE', actif: false },
      { nom: 'Netflix, Spotify, etc.', montant: 25, categorie: 'ABONNEMENTS', typeDepense: 'FIXE', frequence: 'MENSUELLE', actif: true },
    ];
  }

  /**
   * Retourne la liste des dépenses variables types prédéfinies pour démarrer rapidement.
   */
  getDepensesVariablesExemple(): Omit<DepenseType, 'id'>[] {
    return [
      { nom: 'Alimentation / Courses', montant: 400, categorie: 'ALIMENTATION', typeDepense: 'VARIABLE', frequence: 'MENSUELLE', actif: true },
      { nom: 'Carburant', montant: 100, categorie: 'TRANSPORT', typeDepense: 'VARIABLE', frequence: 'MENSUELLE', actif: true },
      { nom: 'Restaurant / Sorties', montant: 80, categorie: 'LOISIRS', typeDepense: 'VARIABLE', frequence: 'MENSUELLE', actif: true },
      { nom: 'Vêtements', montant: 50, categorie: 'AUTRE', typeDepense: 'VARIABLE', frequence: 'MENSUELLE', actif: true },
      { nom: 'Soins personnels', montant: 30, categorie: 'SANTE', typeDepense: 'VARIABLE', frequence: 'MENSUELLE', actif: true },
      { nom: 'Médecin / Pharmacie', montant: 30, categorie: 'SANTE', typeDepense: 'VARIABLE', frequence: 'MENSUELLE', actif: true },
      { nom: 'Sport / Loisirs', montant: 50, categorie: 'LOISIRS', typeDepense: 'VARIABLE', frequence: 'MENSUELLE', actif: true },
      { nom: 'Cadeaux', montant: 30, categorie: 'AUTRE', typeDepense: 'VARIABLE', frequence: 'MENSUELLE', actif: true },
      { nom: 'Divers', montant: 50, categorie: 'AUTRE', typeDepense: 'VARIABLE', frequence: 'MENSUELLE', actif: true },
    ];
  }

  // ============================================================
  // Persistance localStorage (fallback)
  // ============================================================

  /**
   * Charge le mois type courant depuis le localStorage.
   * Retourne null si aucun mois type n'est sauvegardé.
   */
  loadFromStorage(): MoisType | null {
    try {
      const raw = localStorage.getItem(MOIS_TYPE_STORAGE_KEY);
      if (!raw) return null;
      return JSON.parse(raw) as MoisType;
    } catch (error) {
      console.error('MoisTypeService: erreur de lecture localStorage:', error);
      return null;
    }
  }

  /**
   * Sauvegarde le mois type courant dans le localStorage.
   */
  saveToStorage(moisType: MoisType): void {
    try {
      localStorage.setItem(MOIS_TYPE_STORAGE_KEY, JSON.stringify(moisType));
    } catch (error) {
      console.error('MoisTypeService: erreur de sauvegarde localStorage:', error);
    }
  }

  /**
   * Supprime le mois type du localStorage.
   */
  clearStorage(): void {
    localStorage.removeItem(MOIS_TYPE_STORAGE_KEY);
  }

  /**
   * Charge tous les mois types depuis le localStorage.
   */
  private loadAllFromStorage(): MoisType[] {
    try {
      const single = this.loadFromStorage();
      return single ? [single] : [];
    } catch {
      return [];
    }
  }

  /**
   * Sauvegarde tous les mois types dans le localStorage.
   */
  private saveAllToStorage(moisTypes: MoisType[]): void {
    if (moisTypes.length > 0) {
      this.saveToStorage(moisTypes[0]);
    } else {
      this.clearStorage();
    }
  }
}
