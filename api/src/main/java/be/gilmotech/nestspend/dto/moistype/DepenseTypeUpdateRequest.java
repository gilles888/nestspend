package be.gilmotech.nestspend.dto.moistype;

import be.gilmotech.nestspend.domain.enums.CategorieDepense;
import be.gilmotech.nestspend.domain.enums.FrequenceDepense;
import be.gilmotech.nestspend.domain.enums.TypeDepense;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * DTO de requête pour la mise à jour d'une dépense type.
 * Tous les champs sont modifiables sauf l'identifiant et le mois type de rattachement.
 */
@Schema(description = "Requête de mise à jour d'une dépense type")
public record DepenseTypeUpdateRequest(

        @NotBlank(message = "Le nom de la dépense est obligatoire")
        @Size(max = 255, message = "Le nom ne peut pas dépasser 255 caractères")
        @Schema(description = "Libellé de la dépense", example = "Loyer",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String nom,

        @NotNull(message = "Le montant est obligatoire")
        @Positive(message = "Le montant doit être positif")
        @Schema(description = "Montant en centimes pour la période de fréquence", example = "90000",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long montant,

        @NotNull(message = "La catégorie est obligatoire")
        @Schema(description = "Catégorie budgétaire", example = "LOGEMENT",
                requiredMode = Schema.RequiredMode.REQUIRED)
        CategorieDepense categorie,

        @NotNull(message = "Le type de dépense est obligatoire")
        @Schema(description = "Nature : FIXE ou VARIABLE", example = "FIXE",
                requiredMode = Schema.RequiredMode.REQUIRED)
        TypeDepense typeDepense,

        @NotNull(message = "La fréquence est obligatoire")
        @Schema(description = "Fréquence : MENSUELLE, TRIMESTRIELLE ou ANNUELLE", example = "MENSUELLE",
                requiredMode = Schema.RequiredMode.REQUIRED)
        FrequenceDepense frequence
) {
}
