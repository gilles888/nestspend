package be.gilmotech.nestspend.dto.classification;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * DTO for a transaction to be classified.
 */
@Schema(description = "Transaction data for classification suggestion")
public record TransactionToClassify(
        @Schema(description = "Merchant name", example = "DELHAIZE")
        String merchant,

        @Schema(description = "Communication/description", example = "CB 12345")
        String communication,

        @Schema(description = "Counterparty IBAN", example = "BE92123456789012")
        String iban,

        @Schema(description = "Transaction amount in cents", example = "12345")
        Long amount,

        @Schema(description = "Transaction date", example = "2026-01-01")
        LocalDate date
) {
}
