package be.gilmotech.nestspend.dto.account;

import be.gilmotech.nestspend.domain.enums.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new account.
 */
public record AccountCreateRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 80, message = "Name must not exceed 80 characters")
        String name,

        @NotNull(message = "Type is required")
        AccountType type
) {
}
