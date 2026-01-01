package be.gilmotech.nestspend.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response DTO for monthly dashboard aggregations.
 */
@Schema(description = "Monthly dashboard aggregations including income, expenses, and category breakdown")
public record DashboardResponse(
        @Schema(description = "Month in YYYY-MM format", example = "2025-12")
        String month,

        @Schema(description = "Total income in cents for the month", example = "500000")
        Long totalIncomeCents,

        @Schema(description = "Total expenses in cents for the month", example = "350000")
        Long totalExpenseCents,

        @Schema(description = "Net balance in cents (income - expenses)", example = "150000")
        Long netCents,

        @Schema(description = "Breakdown of expenses by category")
        List<CategoryExpenseResponse> expensesByCategory
) {
}
