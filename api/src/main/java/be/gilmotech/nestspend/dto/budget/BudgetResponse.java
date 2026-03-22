package be.gilmotech.nestspend.dto.budget;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de réponse pour un budget mensuel par catégorie.
 * Inclut les dépenses réelles pour permettre le suivi du taux de consommation.
 */
@Schema(description = "Budget mensuel par catégorie avec suivi des dépenses réelles")
public record BudgetResponse(

        @Schema(description = "Identifiant unique du budget")
        UUID id,

        @Schema(description = "UUID de la catégorie")
        UUID categoryId,

        @Schema(description = "Nom de la catégorie", example = "Alimentation")
        String categoryName,

        @Schema(description = "Couleur de la catégorie", example = "#4CAF50")
        String categoryColor,

        @Schema(description = "Mois du budget au format YYYY-MM", example = "2025-03")
        String month,

        @Schema(description = "Montant du budget en centimes", example = "30000")
        Long amountCents,

        @Schema(description = "Dépenses réelles en centimes pour ce mois et cette catégorie", example = "24500")
        Long spentCents,

        @Schema(description = "Montant restant en centimes (peut être négatif si dépassement)", example = "5500")
        Long remainingCents,

        @Schema(description = "Pourcentage du budget consommé (0-100+)", example = "81.67")
        Double consumptionPercent,

        @Schema(description = "Date de création du budget")
        Instant createdAt,

        @Schema(description = "Date de dernière modification du budget")
        Instant updatedAt
) {
}
