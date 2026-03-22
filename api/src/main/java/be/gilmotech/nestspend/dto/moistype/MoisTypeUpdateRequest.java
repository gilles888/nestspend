package be.gilmotech.nestspend.dto.moistype;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * DTO de requête pour la mise à jour d'un mois type budgétaire.
 * Tous les champs sont modifiables sauf l'identifiant.
 */
@Schema(description = "Requête de mise à jour d'un mois type")
public record MoisTypeUpdateRequest(

        @NotBlank(message = "Le nom du mois type est obligatoire")
        @Size(max = 255, message = "Le nom ne peut pas dépasser 255 caractères")
        @Schema(description = "Nom descriptif du mois type", example = "Mois standard 2026",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String nom,

        @NotNull(message = "L'année est obligatoire")
        @Min(value = 2020, message = "L'année doit être supérieure ou égale à 2020")
        @Max(value = 2100, message = "L'année doit être inférieure ou égale à 2100")
        @Schema(description = "Année de référence pour ce mois type", example = "2026",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Integer annee,

        @NotNull(message = "Les revenus sont obligatoires")
        @PositiveOrZero(message = "Les revenus doivent être positifs ou nuls")
        @Schema(description = "Revenus principaux en centimes (salaires, etc.)", example = "300000",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long revenus,

        @PositiveOrZero(message = "Les autres revenus doivent être positifs ou nuls")
        @Schema(description = "Autres revenus en centimes (primes, revenus locatifs, etc.)", example = "50000",
                defaultValue = "0")
        Long autresRevenus
) {
}
