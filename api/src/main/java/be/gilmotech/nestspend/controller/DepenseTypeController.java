package be.gilmotech.nestspend.controller;

import be.gilmotech.nestspend.dto.moistype.DepenseTypeCreateRequest;
import be.gilmotech.nestspend.dto.moistype.DepenseTypeDto;
import be.gilmotech.nestspend.dto.moistype.DepenseTypeUpdateRequest;
import be.gilmotech.nestspend.service.MoisTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Contrôleur REST pour la gestion des dépenses types.
 * Les dépenses types sont des charges récurrentes rattachées à un mois type,
 * utilisées pour calculer les projections financières annuelles.
 *
 * <p>Note : ces routes sont une alternative à /api/mois-type/depenses.
 * Elles permettent une gestion indépendante des dépenses types sans contexte de mois type.</p>
 */
@RestController
@RequestMapping("/api/depenses-type")
@Tag(name = "Dépenses Type", description = "Gestion des dépenses types (charges récurrentes) rattachées aux mois types")
@SecurityRequirement(name = "bearer-jwt")
public class DepenseTypeController {

    private final MoisTypeService moisTypeService;

    public DepenseTypeController(MoisTypeService moisTypeService) {
        this.moisTypeService = moisTypeService;
    }

    @GetMapping
    @Operation(
            summary = "Lister les dépenses types d'un mois type",
            description = "Retourne toutes les dépenses types (actives et inactives) d'un mois type donné, " +
                    "triées par catégorie puis par nom."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Liste récupérée avec succès"),
            @ApiResponse(responseCode = "400", description = "Paramètre moisTypeId manquant ou invalide",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Token JWT manquant ou invalide",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Mois type non trouvé",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<List<DepenseTypeDto>> listerDepensesType(
            @Parameter(description = "UUID du mois type", required = true)
            @RequestParam UUID moisTypeId
    ) {
        return ResponseEntity.ok(moisTypeService.listerDepensesType(moisTypeId));
    }

    @PostMapping
    @Operation(
            summary = "Créer une dépense type",
            description = "Crée une nouvelle dépense type et la rattache au mois type spécifié. " +
                    "Le montant est exprimé en centimes pour la période définie par la fréquence."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Dépense type créée avec succès"),
            @ApiResponse(responseCode = "400", description = "Données invalides",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Token JWT manquant ou invalide",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Mois type non trouvé",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<DepenseTypeDto> creerDepenseType(
            @Valid @RequestBody DepenseTypeCreateRequest request
    ) {
        DepenseTypeDto created = moisTypeService.creerDepenseType(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Modifier une dépense type",
            description = "Met à jour les informations d'une dépense type existante. " +
                    "Le mois type de rattachement ne peut pas être modifié."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dépense type mise à jour avec succès"),
            @ApiResponse(responseCode = "400", description = "Données invalides",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Token JWT manquant ou invalide",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Dépense type non trouvée",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<DepenseTypeDto> mettreAJourDepenseType(
            @Parameter(description = "UUID de la dépense type", required = true)
            @PathVariable UUID id,
            @Valid @RequestBody DepenseTypeUpdateRequest request
    ) {
        return ResponseEntity.ok(moisTypeService.mettreAJourDepenseType(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Supprimer une dépense type",
            description = "Supprime définitivement une dépense type. " +
                    "Pour une désactivation temporaire sans suppression, utiliser PATCH /{id}/toggle."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Dépense type supprimée avec succès"),
            @ApiResponse(responseCode = "401", description = "Token JWT manquant ou invalide",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Dépense type non trouvée",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<Void> supprimerDepenseType(
            @Parameter(description = "UUID de la dépense type", required = true)
            @PathVariable UUID id
    ) {
        moisTypeService.supprimerDepenseType(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    @Operation(
            summary = "Activer / désactiver une dépense type",
            description = "Bascule l'état actif/inactif d'une dépense type. " +
                    "Une dépense inactive est conservée en base mais exclue des calculs de projection."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "État modifié avec succès"),
            @ApiResponse(responseCode = "401", description = "Token JWT manquant ou invalide",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Dépense type non trouvée",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<DepenseTypeDto> basculerActivation(
            @Parameter(description = "UUID de la dépense type", required = true)
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(moisTypeService.basculerActivation(id));
    }
}
