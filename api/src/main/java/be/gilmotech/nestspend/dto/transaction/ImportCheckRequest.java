package be.gilmotech.nestspend.dto.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for checking which transactions already exist (for deduplication).
 */
@Schema(description = "Request body for checking existing transactions before import")
public record ImportCheckRequest(
        @NotNull(message = "Account ID is required")
        @Schema(description = "UUID of the target account", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID accountId,

        @NotNull(message = "Start date is required")
        @Schema(description = "Start date of the period to check (inclusive)", example = "2024-01-01", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate from,

        @NotNull(message = "End date is required")
        @Schema(description = "End date of the period to check (inclusive)", example = "2024-12-31", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate to,

        @NotEmpty(message = "Keys list cannot be empty")
        @Schema(description = "List of deduplication keys to check (format: DATE|AMOUNT|MERCHANT)", requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> keys
) {
}
