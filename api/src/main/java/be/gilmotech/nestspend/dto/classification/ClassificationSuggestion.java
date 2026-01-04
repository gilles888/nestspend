package be.gilmotech.nestspend.dto.classification;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * DTO for a single classification suggestion.
 */
@Schema(description = "Classification suggestion for a transaction")
public record ClassificationSuggestion(
        @Schema(description = "Suggested category ID (null if no match)", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID categoryId,

        @Schema(description = "Confidence score (0-100)", example = "85")
        Integer confidence,

        @Schema(description = "Confidence label", example = "HIGH")
        ConfidenceLabel confidenceLabel,

        @Schema(description = "ID of the rule that matched (if any)", example = "550e8400-e29b-41d4-a716-446655440001")
        UUID ruleId
) {
    public enum ConfidenceLabel {
        HIGH,
        MEDIUM,
        LOW
    }

    /**
     * Create a suggestion with no match.
     */
    public static ClassificationSuggestion noMatch() {
        return new ClassificationSuggestion(null, 0, ConfidenceLabel.LOW, null);
    }

    /**
     * Determine confidence label from score.
     */
    public static ConfidenceLabel labelFromScore(int score) {
        if (score >= 80) {
            return ConfidenceLabel.HIGH;
        } else if (score >= 60) {
            return ConfidenceLabel.MEDIUM;
        } else {
            return ConfidenceLabel.LOW;
        }
    }
}
