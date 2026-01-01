package be.gilmotech.nestspend.dto.account;

import be.gilmotech.nestspend.domain.enums.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new account.
 */
@Schema(description = "Request body for creating a new account")
public record AccountCreateRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 80, message = "Name must not exceed 80 characters")
        @Schema(description = "Name of the account", example = "Main Checking", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 80)
        String name,

        @NotNull(message = "Type is required")
        @Schema(description = "Type of account", example = "BANK", requiredMode = Schema.RequiredMode.REQUIRED)
        AccountType type
) {
}
