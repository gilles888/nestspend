package be.gilmotech.nestspend.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating an existing category.
 */
public record CategoryUpdateRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 80, message = "Name must not exceed 80 characters")
        String name,

        @Size(max = 20, message = "Color must not exceed 20 characters")
        String color,

        @Size(max = 40, message = "Icon must not exceed 40 characters")
        String icon
) {
}
