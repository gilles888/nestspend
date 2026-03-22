package be.gilmotech.nestspend.controller;

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
}
