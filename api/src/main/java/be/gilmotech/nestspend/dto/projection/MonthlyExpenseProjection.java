package be.gilmotech.nestspend.dto.projection;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Point de données pour la projection des dépenses mensuelles et annuelles.
 * Contient les montants réels (si le mois est passé) ou projetés (si le mois est futur).
 */
@Schema(description = "Données de projection pour un mois donné")
public record MonthlyExpenseProjection(

        @Schema(description = "Mois au format YYYY-MM", example = "2025-03")
        String month,

        @Schema(description = "Revenus en centimes pour ce mois", example = "250000")
        Long incomeCents,

        @Schema(description = "Dépenses en centimes pour ce mois", example = "180000")
        Long expenseCents,

        @Schema(description = "Solde net en centimes (revenus - dépenses)", example = "70000")
        Long netCents,

        @Schema(description = "Indique si les données sont réelles (true) ou projetées (false)")
        boolean isActual
) {
}
