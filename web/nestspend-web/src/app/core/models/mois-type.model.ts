/**
 * Modèles TypeScript pour la fonctionnalité "Mois Type".
 * Représente les dépenses types, les mois de référence et les projections annuelles.
 */

/** Catégories possibles pour une dépense type */
export type CategorieDepense =
  | 'LOGEMENT'
  | 'TRANSPORT'
  | 'ALIMENTATION'
  | 'SANTE'
  | 'LOISIRS'
  | 'ABONNEMENTS'
  | 'EPARGNE'
  | 'AUTRE';

/** Type de dépense : fixe (montant constant) ou variable (montant approximatif) */
export type TypeDepense = 'FIXE' | 'VARIABLE';

/** Fréquence de la dépense */
export type FrequenceDepense = 'MENSUELLE' | 'TRIMESTRIELLE' | 'ANNUELLE';

/**
 * Représente une dépense type dans un mois de référence.
 * Peut être fixe (loyer) ou variable (alimentation).
 */
export interface DepenseType {
  /** Identifiant de la dépense type (null si non encore persisté) */
  id?: number;
  /** Libellé de la dépense (ex: "Loyer", "Alimentation") */
  nom: string;
  /** Montant brut de la dépense (selon la fréquence) */
  montant: number;
  /** Catégorie de la dépense */
  categorie: CategorieDepense;
  /** Type de dépense : FIXE ou VARIABLE */
  typeDepense: TypeDepense;
  /** Fréquence de la dépense */
  frequence: FrequenceDepense;
  /** Indique si la dépense est active (incluse dans les calculs) */
  actif: boolean;
  /** Montant mensuel calculé (montant ramené au mois selon la fréquence) */
  montantMensuel?: number;
}

/**
 * Représente un mois de référence (template de budget mensuel).
 * Contient les revenus et la liste des dépenses types.
 */
export interface MoisType {
  /** Identifiant du mois type (null si non encore persisté) */
  id?: number;
  /** Nom du mois type (ex: "Mon mois type 2026") */
  nom: string;
  /** Année de référence */
  annee: number;
  /** Salaire net mensuel en euros */
  revenus: number;
  /** Autres revenus mensuels en euros (loyer perçu, freelance, etc.) */
  autresRevenus: number;
  /** Liste des dépenses types associées */
  depenses?: DepenseType[];
}

/**
 * Données d'un mois dans la projection annuelle.
 */
export interface ProjectionMois {
  /** Numéro du mois (1 = Janvier, 12 = Décembre) */
  mois: number;
  /** Nom du mois en français (ex: "Janvier") */
  nomMois: string;
  /** Revenus prévus pour ce mois */
  revenus: number;
  /** Total des dépenses fixes pour ce mois */
  depensesFixes: number;
  /** Total des dépenses variables pour ce mois */
  depensesVariables: number;
  /** Total de toutes les dépenses */
  totalDepenses: number;
  /** Épargne du mois (revenus - totalDepenses) */
  epargneMois: number;
  /** Épargne cumulée depuis le début de l'année */
  epargneCumulee: number;
}

/**
 * Projection annuelle complète calculée à partir d'un mois type.
 */
export interface ProjectionAnnuelle {
  /** Année projetée */
  annee: number;
  /** Données mois par mois (12 entrées) */
  mois: ProjectionMois[];
  /** Total des revenus sur l'année */
  totalRevenus: number;
  /** Total des dépenses sur l'année */
  totalDepenses: number;
  /** Total de l'épargne sur l'année */
  totalEpargne: number;
  /** Taux d'épargne annuel en pourcentage */
  tauxEpargne: number;
}

/**
 * Résumé financier calculé en temps réel à partir du mois type en cours de saisie.
 */
export interface ResumeFinancier {
  /** Revenus mensuels totaux */
  revenusMensuel: number;
  /** Dépenses fixes mensuelles (actives uniquement, ramenées au mois) */
  depensesFixesMensuel: number;
  /** Dépenses variables mensuelles (actives uniquement, ramenées au mois) */
  depensesVariablesMensuel: number;
  /** Total des dépenses mensuelles */
  totalDepensesMensuel: number;
  /** Épargne mensuelle possible */
  epargneMensuel: number;
  /** Taux d'épargne mensuel en pourcentage */
  tauxEpargneMensuel: number;
  /** Revenus annuels */
  revenusAnnuel: number;
  /** Dépenses fixes annuelles */
  depensesFixesAnnuel: number;
  /** Dépenses variables annuelles */
  depensesVariablesAnnuel: number;
  /** Total des dépenses annuelles */
  totalDepensesAnnuel: number;
  /** Épargne annuelle possible */
  epargneAnnuel: number;
  /** Taux d'épargne annuel en pourcentage */
  tauxEpargneAnnuel: number;
}
