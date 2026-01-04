package be.gilmotech.nestspend.dto.classification;

import be.gilmotech.nestspend.domain.enums.MatchType;
import be.gilmotech.nestspend.domain.enums.RuleField;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.util.UUID;

/**
 * Request DTO for updating a classification rule.
 */
@Schema(description = "Request body for updating a classification rule")
public record ClassificationRuleUpdateRequest(
        @NotNull(message = "Field is required")
        @Schema(description = "Field to match against", example = "MERCHANT", requiredMode = Schema.RequiredMode.REQUIRED)
        RuleField field,

        @NotNull(message = "Match type is required")
        @Schema(description = "Type of pattern matching", example = "CONTAINS", requiredMode = Schema.RequiredMode.REQUIRED)
        MatchType matchType,

        @NotBlank(message = "Pattern is required")
        @Size(max = 255, message = "Pattern must not exceed 255 characters")
        @Schema(description = "Pattern to match", example = "DELHAIZE", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 255)
        String pattern,

        @NotNull(message = "Category ID is required")
        @Schema(description = "Target category ID", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID categoryId,

        @NotNull(message = "Enabled is required")
        @Schema(description = "Whether the rule is enabled", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean enabled,

        @NotNull(message = "Priority is required")
        @Min(value = 0, message = "Priority must be at least 0")
        @Schema(description = "Rule priority (higher = evaluated first)", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer priority,

        @NotNull(message = "Confidence is required")
        @Min(value = 0, message = "Confidence must be at least 0")
        @Max(value = 100, message = "Confidence must be at most 100")
        @Schema(description = "Confidence score for this rule (0-100)", example = "80", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer confidence
) {
}
