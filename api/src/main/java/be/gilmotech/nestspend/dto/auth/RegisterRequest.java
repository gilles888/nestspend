package be.gilmotech.nestspend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 190, message = "Email must not exceed 190 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        String password,

        @NotBlank(message = "Display name is required")
        @Size(max = 80, message = "Display name must not exceed 80 characters")
        String displayName,

        @NotBlank(message = "Household name is required")
        @Size(max = 80, message = "Household name must not exceed 80 characters")
        String householdName
) {
}
