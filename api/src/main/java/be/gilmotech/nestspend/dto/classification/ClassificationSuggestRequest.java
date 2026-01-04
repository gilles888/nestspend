package be.gilmotech.nestspend.dto.classification;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request DTO for classification suggestions.
 */
@Schema(description = "Request body for classification suggestions")
public record ClassificationSuggestRequest(
        @NotEmpty(message = "At least one transaction is required")
        @Valid
        @Schema(description = "List of transactions to classify", requiredMode = Schema.RequiredMode.REQUIRED)
        List<TransactionToClassify> transactions
) {
}
