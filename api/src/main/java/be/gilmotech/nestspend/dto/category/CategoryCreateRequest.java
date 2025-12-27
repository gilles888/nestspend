package be.gilmotech.nestspend.dto.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new category.
 */
@Schema(description = "Request body for creating a new category")
public record CategoryCreateRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 80, message = "Name must not exceed 80 characters")
        @Schema(description = "Name of the category", example = "Food & Groceries", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 80)
        String name,

        @Size(max = 20, message = "Color must not exceed 20 characters")
        @Schema(description = "Color code for the category", example = "#FF5733", maxLength = 20)
        String color,

        @Size(max = 40, message = "Icon must not exceed 40 characters")
        @Schema(description = "Icon identifier for the category", example = "shopping-cart", maxLength = 40)
        String icon
) {
}
