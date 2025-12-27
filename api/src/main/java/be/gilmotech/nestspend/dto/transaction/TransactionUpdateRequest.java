package be.gilmotech.nestspend.dto.transaction;

import be.gilmotech.nestspend.domain.enums.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for updating an existing transaction.
 */
@Schema(description = "Request body for updating a transaction")
public record TransactionUpdateRequest(
        @NotNull(message = "Transaction date is required")
        @Schema(description = "Date of the transaction", example = "2024-01-15", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate txDate,

        @NotNull(message = "Transaction type is required")
        @Schema(description = "Type of transaction", example = "EXPENSE", requiredMode = Schema.RequiredMode.REQUIRED)
        TransactionType type,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        @Schema(description = "Amount in cents (must be positive)", example = "1500", requiredMode = Schema.RequiredMode.REQUIRED)
        Long amountCents,

        @NotNull(message = "Category ID is required")
        @Schema(description = "UUID of the category", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID categoryId,

        @NotNull(message = "Account ID is required")
        @Schema(description = "UUID of the account", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID accountId,

        @Size(max = 120, message = "Merchant must not exceed 120 characters")
        @Schema(description = "Merchant name", example = "Supermarket", maxLength = 120)
        String merchant,

        @Schema(description = "Additional note for the transaction")
        String note
) {
}
