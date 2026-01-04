package be.gilmotech.nestspend.dto.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for bulk importing transactions.
 */
@Schema(description = "Request body for bulk importing transactions")
public record ImportTransactionRequest(
        @NotNull(message = "Account ID is required")
        @Schema(description = "UUID of the target account for all imported transactions", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID accountId,

        @NotEmpty(message = "Items list cannot be empty")
        @Valid
        @Schema(description = "List of transactions to import", requiredMode = Schema.RequiredMode.REQUIRED)
        List<ImportTransactionItem> items
) {
}
