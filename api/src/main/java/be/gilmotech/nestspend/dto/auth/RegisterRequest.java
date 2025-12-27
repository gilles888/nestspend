package be.gilmotech.nestspend.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body for user registration")
public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 190, message = "Email must not exceed 190 characters")
        @Schema(description = "User's email address", example = "john@example.com", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 190)
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        @Schema(description = "User's password (8-100 characters)", example = "mySecurePassword123", requiredMode = Schema.RequiredMode.REQUIRED, minLength = 8, maxLength = 100)
        String password,

        @NotBlank(message = "Display name is required")
        @Size(max = 80, message = "Display name must not exceed 80 characters")
        @Schema(description = "User's display name", example = "John Doe", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 80)
        String displayName,

        @NotBlank(message = "Household name is required")
        @Size(max = 80, message = "Household name must not exceed 80 characters")
        @Schema(description = "Name of the household to create", example = "The Doe Family", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 80)
        String householdName
) {
}
