package be.gilmotech.nestspend.domain.enums;

/**
 * Fréquence de récurrence d'une dépense type.
 * Détermine le coefficient de mensualisation utilisé dans les calculs de projection :
 * - MENSUELLE : le montant s'applique tel quel chaque mois
 * - TRIMESTRIELLE : le montant est divisé par 3 pour obtenir l'équivalent mensuel
 * - ANNUELLE : le montant est divisé par 12 pour obtenir l'équivalent mensuel
 */
public enum FrequenceDepense {
    MENSUELLE,
    TRIMESTRIELLE,
    ANNUELLE
}
