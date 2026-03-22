package be.gilmotech.nestspend.controller;

import be.gilmotech.nestspend.dto.moistype.ProjectionAnnuelleDto;
import be.gilmotech.nestspend.dto.projection.AnnualProjectionResponse;
import be.gilmotech.nestspend.dto.projection.AnnualSavingsResponse;
import be.gilmotech.nestspend.service.ProjectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Year;

/**
 * Contrôleur REST pour les projections de dépenses mensuelles et annuelles.
 * Combine les données réelles des transactions passées et les événements futurs planifiés.
 *
 * <p>Endpoints disponibles :</p>
 * <ul>
 *   <li>GET /api/projections/annual  - Projection annuelle (12 mois)</li>
 *   <li>GET /api/projections/monthly - Alias de la projection annuelle (même données)</li>
 *   <li>GET /api/projections/savings - Projection d'épargne mois par mois avec cumul</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/projections")
@Tag(name = "Projections", description = "Projection des dépenses mensuelles et annuelles combinant données réelles et événements planifiés")
@SecurityRequirement(name = "bearer-jwt")
public class ProjectionController {

    private final ProjectionService projectionService;

    public ProjectionController(ProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @GetMapping("/annual")
    @Operation(
            summary = "Projection annuelle des dépenses",
            description = "Retourne la projection mois par mois pour une année donnée. " +
                    "Les mois passés affichent les données réelles des transactions. " +
                    "Les mois futurs sont calculés à partir des événements récurrents planifiés. " +
                    "Le mois courant combine données réelles et projection."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Projection annuelle générée avec succès"),
            @ApiResponse(responseCode = "400", description = "Année invalide",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Token JWT manquant ou invalide",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<AnnualProjectionResponse> getAnnualProjection(
            @Parameter(description = "Année à projeter (ex : 2025)", example = "2025")
            @RequestParam(required = false)
            Integer year
    ) {
        // Si aucune année n'est fournie, on utilise l'année courante
        int targetYear = (year != null) ? year : Year.now().getValue();
        AnnualProjectionResponse response = projectionService.getAnnualProjection(targetYear);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/monthly")
    @Operation(
            summary = "Projection mensuelle des dépenses (12 mois)",
            description = "Retourne la décomposition mensuelle pour une année donnée. " +
                    "Équivalent à la projection annuelle, exposé sous un endpoint dédié " +
                    "pour la clarté de l'API côté consommateurs. " +
                    "Les mois passés affichent les données réelles, les mois futurs sont projetés."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Projection mensuelle générée avec succès"),
            @ApiResponse(responseCode = "400", description = "Année invalide",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Token JWT manquant ou invalide",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<AnnualProjectionResponse> getMonthlyProjection(
            @Parameter(description = "Année à projeter (ex : 2025)", example = "2025")
            @RequestParam(required = false)
            Integer year
    ) {
        // Si aucune année n'est fournie, on utilise l'année courante
        int targetYear = (year != null) ? year : Year.now().getValue();
        AnnualProjectionResponse response = projectionService.getMonthlyProjection(targetYear);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/savings")
    @Operation(
            summary = "Projection de l'épargne mensuelle et annuelle",
            description = "Calcule l'épargne possible mois par mois (revenus - dépenses) " +
                    "et le cumul annuel. Permet d'estimer la capacité d'épargne sur l'année. " +
                    "Données réelles pour les mois passés, projection pour les mois futurs."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Projection d'épargne générée avec succès"),
            @ApiResponse(responseCode = "400", description = "Année invalide",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Token JWT manquant ou invalide",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<AnnualSavingsResponse> getSavingsProjection(
            @Parameter(description = "Année à projeter (ex : 2025)", example = "2025")
            @RequestParam(required = false)
            Integer year
    ) {
        // Si aucune année n'est fournie, on utilise l'année courante
        int targetYear = (year != null) ? year : Year.now().getValue();
        AnnualSavingsResponse response = projectionService.getSavingsProjection(targetYear);
        return ResponseEntity.ok(response);
    }

    // ==========================================================================
    // Projections basées sur le mois type
    // ==========================================================================

    @GetMapping("/{year}/mois-type")
    @Operation(
            summary = "Projection annuelle depuis le mois type",
            description = "Calcule la projection des 12 mois à partir du mois type défini pour l'année donnée. " +
                    "Retourne les dépenses fixes, variables, l'épargne mensuelle et l'épargne cumulée. " +
                    "Si aucun mois type n'est défini pour l'année, retourne une projection vide (zéros)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Projection générée avec succès"),
            @ApiResponse(responseCode = "400", description = "Année invalide",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Token JWT manquant ou invalide",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<ProjectionAnnuelleDto> getProjectionMoisType(
            @Parameter(description = "Année de projection (ex : 2026)", example = "2026", required = true)
            @PathVariable int year
    ) {
        ProjectionAnnuelleDto response = projectionService.getProjectionMoisType(year);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{year}/mois-type/epargne")
    @Operation(
            summary = "Épargne annuelle depuis le mois type",
            description = "Calcule le total de l'épargne possible sur l'année à partir du mois type. " +
                    "Alias de /{year}/mois-type qui expose les mêmes données — " +
                    "utilisez totalEpargneCents dans la réponse."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Épargne calculée avec succès"),
            @ApiResponse(responseCode = "400", description = "Année invalide",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Token JWT manquant ou invalide",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<ProjectionAnnuelleDto> getEpargneMoisType(
            @Parameter(description = "Année de projection (ex : 2026)", example = "2026", required = true)
            @PathVariable int year
    ) {
        ProjectionAnnuelleDto response = projectionService.getEpargneMoisType(year);
        return ResponseEntity.ok(response);
    }
}
