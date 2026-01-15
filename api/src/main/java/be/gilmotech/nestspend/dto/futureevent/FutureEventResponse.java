package be.gilmotech.nestspend.dto.futureevent;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for a future event.
 */
@Schema(description = "Future event response")
public record FutureEventResponse(
        @Schema(description = "UUID of the event", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID id,

        @Schema(description = "Name of the event", example = "Netflix Subscription")
        String name,

        @Schema(description = "Amount in cents", example = "1599")
        Long amountCents,

        @Schema(description = "Transaction type", example = "EXPENSE")
        String type,

        @Schema(description = "Recurrence periodicity", example = "MONTHLY")
        String periodicity,

        @Schema(description = "Start date of the recurring event", example = "2025-01-01")
        LocalDate startDate,

        @Schema(description = "End date of the recurring event (optional)", example = "2025-12-31")
        LocalDate endDate
) {
}
