package be.gilmotech.nestspend.dto.budget;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO de réponse pour la copie des budgets d'un mois à l'autre.
 * Indique combien de budgets ont été copiés et combien ont été ignorés.
 */
@Schema(description = "Résultat de la copie des budgets d'un mois vers un autre")
public record BudgetCopyResponse(

        @Schema(description = "Mois source au format YYYY-MM", example = "2025-02")
        String fromMonth,

        @Schema(description = "Mois cible au format YYYY-MM", example = "2025-03")
        String toMonth,

        @Schema(description = "Nombre de budgets copiés avec succès", example = "5")
        int copiedCount,

        @Schema(description = "Nombre de budgets ignorés (existaient déjà dans le mois cible)", example = "1")
        int skippedCount,

        @Schema(description = "Message décrivant le résultat de l'opération",
                example = "5 budget(s) copié(s) depuis 2025-02 vers 2025-03 (1 ignoré(s) car déjà existant(s))")
        String message
) {
}
