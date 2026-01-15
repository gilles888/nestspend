package be.gilmotech.nestspend.dto.futureevent;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * Response DTO for budget projections.
 */
@Schema(description = "Budget projection response with projected balances over time")
public record ProjectionResponse(
        @Schema(description = "Start date of the projection period")
        LocalDate startDate,

        @Schema(description = "End date of the projection period")
        LocalDate endDate,

        @Schema(description = "Initial balance in cents at projection start")
        Long initialBalanceCents,

        @Schema(description = "Projected balance in cents at projection end")
        Long finalBalanceCents,

        @Schema(description = "Monthly projection data points")
        List<ProjectionDataPoint> dataPoints
) {
}
