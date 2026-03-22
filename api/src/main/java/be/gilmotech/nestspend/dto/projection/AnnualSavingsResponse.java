package be.gilmotech.nestspend.dto.projection;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Réponse de projection de l'épargne annuelle, mois par mois avec cumul.
 */
@Schema(description = "Projection de l'épargne annuelle avec données mensuelles et cumul")
public record AnnualSavingsResponse(

        @Schema(description = "Année de la projection", example = "2025")
        int year,

        @Schema(description = "Épargne totale réelle en centimes (mois passés uniquement)", example = "420000")
        Long totalActualSavingsCents,

        @Schema(description = "Épargne totale projetée en centimes (année complète)", example = "840000")
        Long totalProjectedSavingsCents,

        @Schema(description = "Détail de l'épargne mois par mois (12 entrées)")
        List<MonthlySavingsProjection> months
) {
}
