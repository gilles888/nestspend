package be.gilmotech.nestspend.dto.account;

import be.gilmotech.nestspend.domain.enums.AccountType;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for account data.
 */
public record AccountResponse(
        UUID id,
        String name,
        AccountType type,
        Instant createdAt
) {
}
