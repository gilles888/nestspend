package be.gilmotech.nestspend.dto.budget;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * DTO de requête pour la création d'un budget mensuel par catégorie.
 */
@Schema(description = "Requête de création d'un budget mensuel")
public record BudgetCreateRequest(

        @NotNull(message = "L'identifiant de la catégorie est obligatoire")
        @Schema(description = "UUID de la catégorie concernée par ce budget", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID categoryId,

        @NotBlank(message = "Le mois est obligatoire")
        @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$",
                message = "Le mois doit être au format YYYY-MM (ex : 2025-03)")
        @Schema(description = "Mois du budget au format YYYY-MM", example = "2025-03",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String month,

        @NotNull(message = "Le montant est obligatoire")
        @Positive(message = "Le montant doit être positif")
        @Schema(description = "Montant du budget en centimes (doit être positif)", example = "30000",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long amountCents
) {
}
