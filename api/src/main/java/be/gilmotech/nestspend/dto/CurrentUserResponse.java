package be.gilmotech.nestspend.dto;

import be.gilmotech.nestspend.domain.enums.UserRole;

import java.util.UUID;

/**
 * Response DTO for the /api/me endpoint containing current user information.
 */
public record CurrentUserResponse(
        UUID userId,
        UUID householdId,
        String email,
        String displayName,
        UserRole role
) {
}
