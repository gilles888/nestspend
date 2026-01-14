package be.gilmotech.nestspend.controller;

import be.gilmotech.nestspend.dto.classification.ClassificationSuggestRequest;
import be.gilmotech.nestspend.dto.classification.ClassificationSuggestResponse;
import be.gilmotech.nestspend.service.ClassificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/classification")
@Tag(name = "Classification", description = "Auto-categorization suggestion endpoints")
@SecurityRequirement(name = "bearer-jwt")
public class ClassificationController {

    private final ClassificationService classificationService;

    public ClassificationController(ClassificationService classificationService) {
        this.classificationService = classificationService;
    }

    @PostMapping("/suggest")
    @Operation(summary = "Get category suggestions", description = "Returns category suggestions for a list of transactions based on classification rules")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved suggestions"),
            @ApiResponse(responseCode = "400", description = "Validation error - Invalid input data",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<ClassificationSuggestResponse> suggest(@Valid @RequestBody ClassificationSuggestRequest request) {
        ClassificationSuggestResponse response = classificationService.suggest(request);
        return ResponseEntity.ok(response);
    }
}
