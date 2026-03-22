package be.gilmotech.nestspend.controller;

import be.gilmotech.nestspend.dto.moistype.DepenseTypeCreateRequest;
import be.gilmotech.nestspend.dto.moistype.DepenseTypeDto;
import be.gilmotech.nestspend.dto.moistype.DepenseTypeUpdateRequest;
import be.gilmotech.nestspend.dto.moistype.MoisTypeCreateRequest;
import be.gilmotech.nestspend.dto.moistype.MoisTypeDto;
import be.gilmotech.nestspend.dto.moistype.MoisTypeUpdateRequest;
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
 * Contrôleur REST pour la gestion des mois types budgétaires.
 * Permet de définir un modèle de mois budgétaire avec des dépenses récurrentes
 * pour calculer des projections mensuelles et annuelles d'épargne.
 */
@RestController
@RequestMapping("/api/mois-type")
@Tag(name = "Mois Type", description = "Gestion des mois types budgétaires et de leurs dépenses récurrentes")
@SecurityRequirement(name = "bearer-jwt")
public class MoisTypeController {

    private final MoisTypeService moisTypeService;

    public MoisTypeController(MoisTypeService moisTypeService) {
        this.moisTypeService = moisTypeService;
    }

    // =========================================================================
    // Routes des mois types
    // =========================================================================

    @GetMapping
    @Operation(
            summary = "Lister les mois types",
            description = "Retourne tous les mois types du foyer courant, triés par année décroissante. " +
                    "Les totaux (dépenses fixes, variables, épargne) sont précalculés. " +
                    "La liste des dépenses n'est pas incluse (utiliser /api/mois-type/{id} pour le détail)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Liste récupérée avec succès"),
            @ApiResponse(responseCode = "401", description = "Token JWT manquant ou invalide",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<List<MoisTypeDto>> listerMoisTypes() {
        return ResponseEntity.ok(moisTypeService.listerMoisTypes());
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Détail d'un mois type",
            description = "Retourne un mois type avec le détail complet de toutes ses dépenses types."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Mois type récupéré avec succès"),
            @ApiResponse(responseCode = "401", description = "Token JWT manquant ou invalide",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Mois type non trouvé",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<MoisTypeDto> getMoisTypeDetail(
            @Parameter(description = "UUID du mois type", required = true)
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(moisTypeService.getMoisTypeDetail(id));
    }

    @PostMapping
    @Operation(
            summary = "Créer un mois type",
            description = "Crée un nouveau mois type budgétaire pour le foyer courant. " +
                    "Les dépenses types peuvent être ajoutées ensuite via POST /api/mois-type/depenses."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Mois type créé avec succès"),
            @ApiResponse(responseCode = "400", description = "Données invalides",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Token JWT manquant ou invalide",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<MoisTypeDto> creerMoisType(
            @Valid @RequestBody MoisTypeCreateRequest request
    ) {
        MoisTypeDto created = moisTypeService.creerMoisType(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Mettre à jour un mois type",
            description = "Met à jour les informations générales d'un mois type (nom, année, revenus). " +
                    "Les dépenses types sont gérées séparément via /api/mois-type/depenses."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Mois type mis à jour avec succès"),
            @ApiResponse(responseCode = "400", description = "Données invalides",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Token JWT manquant ou invalide",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Mois type non trouvé",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<MoisTypeDto> mettreAJourMoisType(
            @Parameter(description = "UUID du mois type", required = true)
            @PathVariable UUID id,
            @Valid @RequestBody MoisTypeUpdateRequest request
    ) {
        return ResponseEntity.ok(moisTypeService.mettreAJourMoisType(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Supprimer un mois type",
            description = "Supprime un mois type et toutes ses dépenses types associées (suppression en cascade)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Mois type supprimé avec succès"),
            @ApiResponse(responseCode = "401", description = "Token JWT manquant ou invalide",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Mois type non trouvé",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<Void> supprimerMoisType(
            @Parameter(description = "UUID du mois type", required = true)
            @PathVariable UUID id
    ) {
        moisTypeService.supprimerMoisType(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Routes des dépenses types
    // =========================================================================

    @PostMapping("/depenses")
    @Operation(
            summary = "Ajouter une dépense type",
            description = "Crée une nouvelle dépense type et la rattache au mois type spécifié. " +
                    "Le montant est exprimé en centimes pour la période de fréquence."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Dépense type créée avec succès"),
            @ApiResponse(responseCode = "400", description = "Données invalides (montant négatif, etc.)",
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

    @PutMapping("/depenses/{id}")
    @Operation(
            summary = "Mettre à jour une dépense type",
            description = "Met à jour les informations d'une dépense type existante. " +
                    "Le mois type de rattachement n'est pas modifiable."
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

    @PatchMapping("/depenses/{id}/actif")
    @Operation(
            summary = "Activer ou désactiver une dépense type",
            description = "Modifie l'état actif d'une dépense type. " +
                    "Une dépense inactive est exclue des calculs de projection mais conservée en base."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "État modifié avec succès"),
            @ApiResponse(responseCode = "401", description = "Token JWT manquant ou invalide",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Dépense type non trouvée",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<DepenseTypeDto> changerEtatDepenseType(
            @Parameter(description = "UUID de la dépense type", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Nouvel état de la dépense : true = active, false = inactive", required = true)
            @RequestParam boolean actif
    ) {
        return ResponseEntity.ok(moisTypeService.changerEtatDepenseType(id, actif));
    }

    @DeleteMapping("/depenses/{id}")
    @Operation(
            summary = "Supprimer une dépense type",
            description = "Supprime définitivement une dépense type. " +
                    "Pour une désactivation temporaire, utiliser PATCH /api/mois-type/depenses/{id}/actif."
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
}
