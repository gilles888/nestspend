package be.gilmotech.nestspend.dto.transaction;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response DTO for the duplicate check operation.
 */
@Schema(description = "Response containing keys of transactions that already exist")
public record ImportCheckResponse(
        @Schema(description = "List of deduplication keys that already exist in the database")
        List<String> existingKeys
) {
}
