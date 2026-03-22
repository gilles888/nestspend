package be.gilmotech.nestspend.domain.enums;

/**
 * Nature d'une dépense type : fixe (montant constant) ou variable (montant estimé).
 * Les dépenses fixes permettent une projection plus fiable que les variables.
 */
public enum TypeDepense {
    FIXE,
    VARIABLE
}
