package be.gilmotech.nestspend.dto.moistype;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO de projection budgétaire pour un mois donné, calculée à partir d'un mois type.
 * Contient toutes les données nécessaires pour afficher le budget prévisionnel d'un mois
 * et le cumul d'épargne depuis le début de l'année.
 */
@Schema(description = "Projection budgétaire mensuelle calculée depuis un mois type")
public record ProjectionMoisDto(

        @Schema(description = "Mois au format YYYY-MM", example = "2026-03")
        String mois,

        @Schema(description = "Total des revenus en centimes pour ce mois", example = "350000")
        Long revenusCents,

        @Schema(description = "Total mensuel des dépenses fixes en centimes", example = "180000")
        Long depensesFixesCents,

        @Schema(description = "Total mensuel des dépenses variables en centimes", example = "80000")
        Long depensesVariablesCents,

        @Schema(description = "Total de toutes les dépenses en centimes", example = "260000")
        Long totalDepensesCents,

        @Schema(description = "Épargne possible ce mois en centimes (revenus - dépenses)", example = "90000")
        Long epargneMoisCents,

        @Schema(description = "Épargne cumulée depuis janvier de l'année en centimes", example = "270000")
        Long epargneCumuleeCents
) {
}
