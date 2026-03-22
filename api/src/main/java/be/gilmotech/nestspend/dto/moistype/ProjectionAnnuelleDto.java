package be.gilmotech.nestspend.dto.moistype;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * DTO de projection budgétaire annuelle calculée à partir d'un mois type.
 * Contient les 12 mois projetés et les totaux annuels.
 */
@Schema(description = "Projection budgétaire annuelle calculée depuis un mois type")
public record ProjectionAnnuelleDto(

        @Schema(description = "Année de la projection", example = "2026")
        Integer annee,

        @Schema(description = "Données mois par mois (12 entrées)")
        List<ProjectionMoisDto> mois,

        @Schema(description = "Total des revenus annuels en centimes", example = "4200000")
        Long totalRevenusCents,

        @Schema(description = "Total annuel des dépenses en centimes", example = "3120000")
        Long totalDepensesCents,

        @Schema(description = "Total annuel de l'épargne possible en centimes", example = "1080000")
        Long totalEpargneCents,

        @Schema(description = "Taux d'épargne annuel en pourcentage", example = "25.71")
        Double tauxEpargne
) {
}
