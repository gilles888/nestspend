package be.gilmotech.nestspend.dto.futureevent;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Data point for budget projection.
 */
@Schema(description = "Single data point in budget projection")
public record ProjectionDataPoint(
        @Schema(description = "Date of this data point", example = "2025-01-31")
        LocalDate date,

        @Schema(description = "Projected total income in cents for this period", example = "500000")
        Long projectedIncomeCents,

        @Schema(description = "Projected total expenses in cents for this period", example = "350000")
        Long projectedExpenseCents,

        @Schema(description = "Projected balance in cents at this date", example = "150000")
        Long projectedBalanceCents
) {
}
