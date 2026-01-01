package be.gilmotech.nestspend.dto.account;

import be.gilmotech.nestspend.domain.enums.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for account data.
 */
@Schema(description = "Account response data")
public record AccountResponse(
        @Schema(description = "Unique identifier of the account", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID id,

        @Schema(description = "Name of the account", example = "Main Checking")
        String name,

        @Schema(description = "Type of account", example = "BANK")
        AccountType type,

        @Schema(description = "Timestamp when the account was created")
        Instant createdAt
) {
}
