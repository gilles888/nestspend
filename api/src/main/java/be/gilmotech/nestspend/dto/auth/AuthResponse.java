package be.gilmotech.nestspend.dto.auth;

import be.gilmotech.nestspend.domain.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Authentication response containing JWT token and user details")
public record AuthResponse(
        @Schema(description = "JWT Bearer token for authentication", example = "eyJhbGciOiJIUzM4NCJ9...")
        String token,

        @Schema(description = "Unique identifier of the user", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID userId,

        @Schema(description = "Unique identifier of the user's household", example = "550e8400-e29b-41d4-a716-446655440001")
        UUID householdId,

        @Schema(description = "Display name of the user", example = "John Doe")
        String displayName,

        @Schema(description = "Role of the user within the household", example = "ADMIN")
        UserRole role
) {
}
