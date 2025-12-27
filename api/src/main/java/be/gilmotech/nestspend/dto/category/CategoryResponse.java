package be.gilmotech.nestspend.dto.category;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for category data.
 */
@Schema(description = "Category response data")
public record CategoryResponse(
        @Schema(description = "Unique identifier of the category", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID id,

        @Schema(description = "Name of the category", example = "Food & Groceries")
        String name,

        @Schema(description = "Color code for the category", example = "#FF5733")
        String color,

        @Schema(description = "Icon identifier for the category", example = "shopping-cart")
        String icon,

        @Schema(description = "Timestamp when the category was created")
        Instant createdAt
) {
}
