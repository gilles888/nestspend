package be.gilmotech.nestspend.dto.transaction;

import be.gilmotech.nestspend.domain.enums.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO representing a single transaction item to be imported.
 */
@Schema(description = "Single transaction item for bulk import")
public record ImportTransactionItem(
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

        @Schema(description = "UUID of the category (optional for import)")
        UUID categoryId,

        @Size(max = 120, message = "Merchant must not exceed 120 characters")
        @Schema(description = "Merchant/counterparty name", example = "Carrefour", maxLength = 120)
        String merchant,

        @Schema(description = "Additional note/communication from bank statement")
        String note,

        @Size(max = 100, message = "External ID must not exceed 100 characters")
        @Schema(description = "External reference ID from bank (for deduplication)", example = "BANK-REF-12345", maxLength = 100)
        String externalId,

        @Size(max = 34, message = "IBAN must not exceed 34 characters")
        @Schema(description = "Counterparty IBAN if available", example = "BE68539007547034", maxLength = 34)
        String counterpartyIban
) {
}
