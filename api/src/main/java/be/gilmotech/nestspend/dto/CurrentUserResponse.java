package be.gilmotech.nestspend.dto;

import be.gilmotech.nestspend.domain.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Response DTO for the /api/me endpoint containing current user information.
 */
@Schema(description = "Current authenticated user information")
public record CurrentUserResponse(
        @Schema(description = "Unique identifier of the user", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID userId,

        @Schema(description = "Unique identifier of the user's household", example = "550e8400-e29b-41d4-a716-446655440001")
        UUID householdId,

        @Schema(description = "User's email address", example = "john@example.com")
        String email,

        @Schema(description = "User's display name", example = "John Doe")
        String displayName,

        @Schema(description = "User's role within the household", example = "ADMIN")
        UserRole role
) {
}
