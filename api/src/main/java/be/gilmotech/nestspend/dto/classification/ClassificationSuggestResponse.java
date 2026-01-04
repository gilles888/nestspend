package be.gilmotech.nestspend.dto.classification;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response DTO for classification suggestions.
 */
@Schema(description = "Classification suggestions response")
public record ClassificationSuggestResponse(
        @Schema(description = "List of suggestions, one per input transaction")
        List<ClassificationSuggestion> suggestions
) {
}
