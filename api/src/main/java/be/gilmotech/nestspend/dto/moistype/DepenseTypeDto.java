package be.gilmotech.nestspend.dto.moistype;

import be.gilmotech.nestspend.domain.enums.CategorieDepense;
import be.gilmotech.nestspend.domain.enums.FrequenceDepense;
import be.gilmotech.nestspend.domain.enums.TypeDepense;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de réponse pour une dépense type.
 * Inclut le montant mensuel équivalent calculé selon la fréquence.
 */
@Schema(description = "Dépense type avec montant mensuel équivalent calculé")
public record DepenseTypeDto(

        @Schema(description = "Identifiant unique de la dépense type")
        UUID id,

        @Schema(description = "Identifiant du mois type auquel appartient cette dépense")
        UUID moisTypeId,

        @Schema(description = "Libellé de la dépense", example = "Loyer")
        String nom,

        @Schema(description = "Montant de la dépense en centimes pour la période de fréquence", example = "90000")
        Long montant,

        @Schema(description = "Montant mensuel équivalent en centimes (tenant compte de la fréquence)", example = "90000")
        Long montantMensuelCents,

        @Schema(description = "Catégorie budgétaire de la dépense")
        CategorieDepense categorie,

        @Schema(description = "Nature de la dépense : FIXE ou VARIABLE")
        TypeDepense typeDepense,

        @Schema(description = "Fréquence de récurrence : MENSUELLE, TRIMESTRIELLE ou ANNUELLE")
        FrequenceDepense frequence,

        @Schema(description = "Indique si la dépense est active (incluse dans les calculs)")
        Boolean actif,

        @Schema(description = "Date de création")
        Instant createdAt,

        @Schema(description = "Date de dernière modification")
        Instant updatedAt
) {
}
