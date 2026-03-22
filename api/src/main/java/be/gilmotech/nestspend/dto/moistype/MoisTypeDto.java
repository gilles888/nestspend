package be.gilmotech.nestspend.dto.moistype;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO de réponse pour un mois type budgétaire.
 * Inclut les totaux calculés (dépenses fixes, variables, épargne possible)
 * pour un affichage direct côté frontend sans recalcul.
 */
@Schema(description = "Mois type budgétaire avec totaux calculés")
public record MoisTypeDto(

        @Schema(description = "Identifiant unique du mois type")
        UUID id,

        @Schema(description = "Nom descriptif du mois type", example = "Mois standard 2026")
        String nom,

        @Schema(description = "Année de référence", example = "2026")
        Integer annee,

        @Schema(description = "Revenus principaux en centimes", example = "300000")
        Long revenus,

        @Schema(description = "Autres revenus en centimes", example = "50000")
        Long autresRevenus,

        @Schema(description = "Total des revenus en centimes (revenus + autresRevenus)", example = "350000")
        Long totalRevenusCents,

        @Schema(description = "Total mensuel des dépenses fixes en centimes", example = "180000")
        Long totalFixesCents,

        @Schema(description = "Total mensuel des dépenses variables en centimes", example = "80000")
        Long totalVariablesCents,

        @Schema(description = "Total mensuel de toutes les dépenses en centimes", example = "260000")
        Long totalDepensesCents,

        @Schema(description = "Épargne possible par mois en centimes (revenus - dépenses)", example = "90000")
        Long epargnePossibleCents,

        @Schema(description = "Taux d'épargne en pourcentage (épargne / revenus)", example = "25.71")
        Double tauxEpargne,

        @Schema(description = "Liste des dépenses types rattachées (chargée uniquement sur /detail)")
        List<DepenseTypeDto> depenses,

        @Schema(description = "Date de création")
        Instant createdAt,

        @Schema(description = "Date de dernière modification")
        Instant updatedAt
) {
}
