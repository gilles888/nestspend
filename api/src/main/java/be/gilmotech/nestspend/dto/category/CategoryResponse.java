package be.gilmotech.nestspend.dto.category;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for category data.
 */
public record CategoryResponse(
        UUID id,
        String name,
        String color,
        String icon,
        Instant createdAt
) {
}
