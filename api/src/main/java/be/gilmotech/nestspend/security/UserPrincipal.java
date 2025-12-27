package be.gilmotech.nestspend.security;

import be.gilmotech.nestspend.domain.enums.UserRole;

import java.util.UUID;

/**
 * Represents the authenticated user's principal information from the security context.
 */
public record UserPrincipal(
        UUID userId,
        UUID householdId,
        UserRole role
) {
}
