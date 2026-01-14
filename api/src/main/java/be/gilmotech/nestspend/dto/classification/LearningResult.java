package be.gilmotech.nestspend.dto.classification;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response DTO for the auto-learning endpoint.
 */
@Schema(description = "Result of the auto-learning process")
public record LearningResult(
        @Schema(description = "Number of rules created", example = "3")
        int rulesCreated,

        @Schema(description = "Number of rules updated", example = "1")
        int rulesUpdated,

        @Schema(description = "Number of merchants ignored (insufficient data or ratio)", example = "5")
        int merchantsIgnored,

        @Schema(description = "Total transactions analyzed", example = "150")
        int transactionsAnalyzed,

        @Schema(description = "Details of created rules")
        List<RuleDetail> createdRules,

        @Schema(description = "Details of updated rules")
        List<RuleDetail> updatedRules
) {
    /**
     * Detail of a created or updated rule.
     */
    @Schema(description = "Detail of a rule")
    public record RuleDetail(
            @Schema(description = "Merchant pattern", example = "DELHAIZE")
            String pattern,

            @Schema(description = "Category name", example = "Alimentation")
            String categoryName,

            @Schema(description = "Confidence score", example = "92")
            int confidence,

            @Schema(description = "Number of transactions that contributed", example = "12")
            int transactionCount
    ) {}
}
