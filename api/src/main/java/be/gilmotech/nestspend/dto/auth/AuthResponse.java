package be.gilmotech.nestspend.dto.auth;

import be.gilmotech.nestspend.domain.enums.UserRole;

import java.util.UUID;

public record AuthResponse(
        String token,
        UUID userId,
        UUID householdId,
        String displayName,
        UserRole role
) {
}
