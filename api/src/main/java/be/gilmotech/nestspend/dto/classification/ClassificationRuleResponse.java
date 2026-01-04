package be.gilmotech.nestspend.dto.classification;

import be.gilmotech.nestspend.domain.enums.MatchType;
import be.gilmotech.nestspend.domain.enums.RuleField;
import be.gilmotech.nestspend.domain.enums.RuleSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for classification rule data.
 */
@Schema(description = "Classification rule response data")
public record ClassificationRuleResponse(
        @Schema(description = "Unique identifier of the rule", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID id,

        @Schema(description = "Field to match against", example = "MERCHANT")
        RuleField field,

        @Schema(description = "Type of pattern matching", example = "CONTAINS")
        MatchType matchType,

        @Schema(description = "Pattern to match", example = "DELHAIZE")
        String pattern,

        @Schema(description = "Target category ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID categoryId,

        @Schema(description = "Target category name", example = "Alimentation")
        String categoryName,

        @Schema(description = "Whether the rule is enabled", example = "true")
        Boolean enabled,

        @Schema(description = "Rule priority (higher = evaluated first)", example = "100")
        Integer priority,

        @Schema(description = "Confidence score for this rule (0-100)", example = "80")
        Integer confidence,

        @Schema(description = "Source of the rule (USER or AUTO)", example = "USER")
        RuleSource source,

        @Schema(description = "Timestamp when the rule was created")
        Instant createdAt,

        @Schema(description = "Timestamp when the rule was last updated")
        Instant updatedAt
) {
}
