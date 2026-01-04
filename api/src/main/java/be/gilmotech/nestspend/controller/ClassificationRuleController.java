package be.gilmotech.nestspend.controller;

import be.gilmotech.nestspend.dto.classification.ClassificationRuleCreateRequest;
import be.gilmotech.nestspend.dto.classification.ClassificationRuleResponse;
import be.gilmotech.nestspend.dto.classification.ClassificationRuleUpdateRequest;
import be.gilmotech.nestspend.service.ClassificationService;
import io.swagger.v3.oas.annotations.Operation;
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

@RestController
@RequestMapping("/api/classification-rules")
@Tag(name = "Classification Rules", description = "CRUD endpoints for classification rules")
@SecurityRequirement(name = "bearer-jwt")
public class ClassificationRuleController {

    private final ClassificationService classificationService;

    public ClassificationRuleController(ClassificationService classificationService) {
        this.classificationService = classificationService;
    }

    @GetMapping
    @Operation(summary = "Get all classification rules", description = "Returns all classification rules for the current user's household")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved classification rules"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<List<ClassificationRuleResponse>> getAllRules() {
        List<ClassificationRuleResponse> rules = classificationService.getAllRules();
        return ResponseEntity.ok(rules);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get classification rule by ID", description = "Returns a classification rule by its ID for the current user's household")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved classification rule"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Not Found - Classification rule not found or belongs to different household",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<ClassificationRuleResponse> getRuleById(@PathVariable UUID id) {
        ClassificationRuleResponse rule = classificationService.getRuleById(id);
        return ResponseEntity.ok(rule);
    }

    @PostMapping
    @Operation(summary = "Create a new classification rule", description = "Creates a new classification rule for the current user's household")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Successfully created classification rule"),
            @ApiResponse(responseCode = "400", description = "Validation error - Invalid input data",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Not Found - Category not found",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<ClassificationRuleResponse> createRule(@Valid @RequestBody ClassificationRuleCreateRequest request) {
        ClassificationRuleResponse rule = classificationService.createRule(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(rule);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a classification rule", description = "Updates an existing classification rule for the current user's household")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully updated classification rule"),
            @ApiResponse(responseCode = "400", description = "Validation error - Invalid input data",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Not Found - Classification rule or category not found",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<ClassificationRuleResponse> updateRule(
            @PathVariable UUID id,
            @Valid @RequestBody ClassificationRuleUpdateRequest request) {
        ClassificationRuleResponse rule = classificationService.updateRule(id, request);
        return ResponseEntity.ok(rule);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a classification rule", description = "Deletes a classification rule for the current user's household")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Successfully deleted classification rule"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Not Found - Classification rule not found or belongs to different household",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id) {
        classificationService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }
}
