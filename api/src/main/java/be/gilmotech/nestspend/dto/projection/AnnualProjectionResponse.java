package be.gilmotech.nestspend.dto.projection;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Réponse de projection annuelle des revenus et dépenses.
 * Combine les données réelles des mois passés et les projections des mois futurs.
 */
@Schema(description = "Projection annuelle des revenus et dépenses mois par mois")
public record AnnualProjectionResponse(

        @Schema(description = "Année de la projection", example = "2025")
        int year,

        @Schema(description = "Total des revenus réels en centimes (mois passés uniquement)", example = "900000")
        Long totalActualIncomeCents,

        @Schema(description = "Total des dépenses réelles en centimes (mois passés uniquement)", example = "650000")
        Long totalActualExpenseCents,

        @Schema(description = "Total des revenus projetés en centimes (année complète)", example = "3000000")
        Long totalProjectedIncomeCents,

        @Schema(description = "Total des dépenses projetées en centimes (année complète)", example = "2200000")
        Long totalProjectedExpenseCents,

        @Schema(description = "Solde net projeté pour l'année complète en centimes", example = "800000")
        Long totalProjectedNetCents,

        @Schema(description = "Données mois par mois (12 entrées)")
        List<MonthlyExpenseProjection> months
) {
}
