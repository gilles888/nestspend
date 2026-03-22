package be.gilmotech.nestspend.dto.projection;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Point de données pour la projection de l'épargne mois par mois.
 * Contient l'épargne du mois et l'épargne cumulée depuis le début de l'année.
 */
@Schema(description = "Données d'épargne pour un mois donné, avec cumul annuel")
public record MonthlySavingsProjection(

        @Schema(description = "Mois au format YYYY-MM", example = "2025-03")
        String month,

        @Schema(description = "Revenus en centimes pour ce mois", example = "250000")
        Long incomeCents,

        @Schema(description = "Dépenses en centimes pour ce mois", example = "180000")
        Long expenseCents,

        @Schema(description = "Épargne du mois en centimes (revenus - dépenses)", example = "70000")
        Long savingsCents,

        @Schema(description = "Épargne cumulée depuis le début de l'année en centimes", example = "210000")
        Long cumulativeSavingsCents,

        @Schema(description = "Indique si les données sont réelles (true) ou projetées (false)")
        boolean isActual
) {
}
