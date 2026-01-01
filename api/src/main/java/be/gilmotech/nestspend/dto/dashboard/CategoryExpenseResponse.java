package be.gilmotech.nestspend.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Response DTO for category expense breakdown in dashboard.
 */
@Schema(description = "Category expense breakdown for monthly dashboard")
public record CategoryExpenseResponse(
        @Schema(description = "UUID of the category", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID categoryId,

        @Schema(description = "Name of the category", example = "Courses")
        String categoryName,

        @Schema(description = "Total expense amount in cents for this category", example = "12345")
        Long amountCents
) {
}
