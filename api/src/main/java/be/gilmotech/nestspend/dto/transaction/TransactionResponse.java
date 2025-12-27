package be.gilmotech.nestspend.dto.transaction;

import be.gilmotech.nestspend.domain.enums.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for transaction data.
 */
@Schema(description = "Transaction response data")
public record TransactionResponse(
        @Schema(description = "Unique identifier of the transaction")
        UUID id,

        @Schema(description = "Date of the transaction", example = "2024-01-15")
        LocalDate txDate,

        @Schema(description = "Type of transaction", example = "EXPENSE")
        TransactionType type,

        @Schema(description = "Amount in cents", example = "1500")
        Long amountCents,

        @Schema(description = "UUID of the category")
        UUID categoryId,

        @Schema(description = "Name of the category", example = "Food")
        String categoryName,

        @Schema(description = "UUID of the account")
        UUID accountId,

        @Schema(description = "Name of the account", example = "Main Checking")
        String accountName,

        @Schema(description = "Merchant name", example = "Supermarket")
        String merchant,

        @Schema(description = "Additional note")
        String note,

        @Schema(description = "UUID of the user who created the transaction")
        UUID createdById,

        @Schema(description = "Display name of the user who created the transaction")
        String createdByName,

        @Schema(description = "Timestamp when the transaction was created")
        Instant createdAt,

        @Schema(description = "Timestamp when the transaction was last updated")
        Instant updatedAt
) {
}
