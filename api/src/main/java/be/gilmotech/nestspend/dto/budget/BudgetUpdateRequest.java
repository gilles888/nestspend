package be.gilmotech.nestspend.dto.budget;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * DTO de requête pour la mise à jour du montant d'un budget existant.
 * La catégorie et le mois ne sont pas modifiables (ils constituent la clé métier).
 */
@Schema(description = "Requête de mise à jour d'un budget mensuel")
public record BudgetUpdateRequest(

        @NotNull(message = "Le montant est obligatoire")
        @Positive(message = "Le montant doit être positif")
        @Schema(description = "Nouveau montant du budget en centimes", example = "35000",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long amountCents
) {
}
