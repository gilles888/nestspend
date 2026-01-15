package be.gilmotech.nestspend.dto.futureevent;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

/**
 * Request DTO for updating a future event.
 */
@Schema(description = "Request to update an existing future event")
public record FutureEventUpdateRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 120, message = "Name must be between 2 and 120 characters")
        @Schema(description = "Name of the event", example = "Netflix Subscription")
        String name,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        @Schema(description = "Amount in cents", example = "1599")
        Long amountCents,

        @NotBlank(message = "Type is required")
        @Pattern(regexp = "^(INCOME|EXPENSE)$", message = "Type must be INCOME or EXPENSE")
        @Schema(description = "Transaction type", example = "EXPENSE")
        String type,

        @NotBlank(message = "Periodicity is required")
        @Pattern(regexp = "^(WEEKLY|MONTHLY|QUARTERLY|YEARLY)$", message = "Periodicity must be WEEKLY, MONTHLY, QUARTERLY, or YEARLY")
        @Schema(description = "Recurrence periodicity", example = "MONTHLY")
        String periodicity,

        @NotNull(message = "Start date is required")
        @Schema(description = "Start date of the recurring event", example = "2025-01-01")
        LocalDate startDate,

        @Schema(description = "End date of the recurring event (optional)", example = "2025-12-31")
        LocalDate endDate
) {
}
