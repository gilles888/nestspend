package be.gilmotech.nestspend.controller;

import be.gilmotech.nestspend.dto.budget.BudgetCreateRequest;
import be.gilmotech.nestspend.dto.budget.BudgetResponse;
import be.gilmotech.nestspend.dto.budget.BudgetSummaryResponse;
import be.gilmotech.nestspend.dto.budget.BudgetUpdateRequest;
import be.gilmotech.nestspend.service.BudgetService;
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

import java.util.UUID;

/**
 * Contrôleur REST pour la gestion des budgets mensuels par catégorie.
 * Permet de définir des plafonds de dépenses et de suivre leur consommation.
 */
@RestController
@RequestMapping("/api/budgets")
@Tag(name = "Budgets", description = "Gestion des budgets mensuels par catégorie avec suivi des dépenses réelles")
@SecurityRequirement(name = "bearer-jwt")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    @Operation(
            summary = "Résumé des budgets d'un mois",
            description = "Retourne tous les budgets du foyer pour le mois spécifié, " +
                    "incluant les dépenses réelles et le taux de consommation par catégorie."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Résumé récupéré avec succès"),
            @ApiResponse(responseCode = "400", description = "Format de mois invalide (attendu : YYYY-MM)",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Token JWT manquant ou invalide",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<BudgetSummaryResponse> getBudgetSummary(
            @Parameter(description = "Mois au format YYYY-MM", example = "2025-03", required = true)
            @RequestParam String month
    ) {
        BudgetSummaryResponse summary = budgetService.getBudgetSummary(month);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Détail d'un budget",
            description = "Retourne un budget spécifique avec les dépenses réelles du mois concerné."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Budget récupéré avec succès"),
            @ApiResponse(responseCode = "401", description = "Token JWT manquant ou invalide",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Budget non trouvé",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<BudgetResponse> getBudgetById(
            @Parameter(description = "UUID du budget", required = true)
            @PathVariable UUID id
    ) {
        BudgetResponse budget = budgetService.getBudgetById(id);
        return ResponseEntity.ok(budget);
    }

    @PostMapping
    @Operation(
            summary = "Créer un budget",
            description = "Crée un nouveau budget mensuel pour une catégorie. " +
                    "Un seul budget par catégorie et par mois est autorisé."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Budget créé avec succès"),
            @ApiResponse(responseCode = "400", description = "Données invalides (montant négatif, format de mois incorrect, etc.)",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Token JWT manquant ou invalide",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Catégorie non trouvée",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "409", description = "Un budget existe déjà pour cette catégorie et ce mois",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<BudgetResponse> createBudget(
            @Valid @RequestBody BudgetCreateRequest request
    ) {
        BudgetResponse budget = budgetService.createBudget(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(budget);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Mettre à jour un budget",
            description = "Met à jour le montant d'un budget existant. " +
                    "La catégorie et le mois ne sont pas modifiables."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Budget mis à jour avec succès"),
            @ApiResponse(responseCode = "400", description = "Données invalides",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Token JWT manquant ou invalide",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Budget non trouvé",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<BudgetResponse> updateBudget(
            @Parameter(description = "UUID du budget", required = true)
            @PathVariable UUID id,
            @Valid @RequestBody BudgetUpdateRequest request
    ) {
        BudgetResponse budget = budgetService.updateBudget(id, request);
        return ResponseEntity.ok(budget);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Supprimer un budget",
            description = "Supprime un budget mensuel. Les transactions existantes ne sont pas affectées."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Budget supprimé avec succès"),
            @ApiResponse(responseCode = "401", description = "Token JWT manquant ou invalide",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Budget non trouvé",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<Void> deleteBudget(
            @Parameter(description = "UUID du budget", required = true)
            @PathVariable UUID id
    ) {
        budgetService.deleteBudget(id);
        return ResponseEntity.noContent().build();
    }
}
