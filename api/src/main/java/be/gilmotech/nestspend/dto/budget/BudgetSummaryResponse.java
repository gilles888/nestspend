package be.gilmotech.nestspend.dto.budget;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * DTO de réponse résumant l'ensemble des budgets d'un mois donné
 * avec les totaux agrégés.
 */
@Schema(description = "Résumé des budgets pour un mois donné avec totaux agrégés")
public record BudgetSummaryResponse(

        @Schema(description = "Mois au format YYYY-MM", example = "2025-03")
        String month,

        @Schema(description = "Total des montants budgétés en centimes", example = "150000")
        Long totalBudgetedCents,

        @Schema(description = "Total des dépenses réelles en centimes", example = "112000")
        Long totalSpentCents,

        @Schema(description = "Total restant en centimes", example = "38000")
        Long totalRemainingCents,

        @Schema(description = "Pourcentage global de consommation des budgets", example = "74.67")
        Double overallConsumptionPercent,

        @Schema(description = "Détail des budgets par catégorie")
        List<BudgetResponse> budgets
) {
}
