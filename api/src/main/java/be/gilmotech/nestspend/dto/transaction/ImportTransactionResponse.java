package be.gilmotech.nestspend.dto.transaction;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response DTO for bulk import operation.
 */
@Schema(description = "Response for bulk import operation showing results")
public record ImportTransactionResponse(
        @Schema(description = "Number of transactions successfully created", example = "45")
        int createdCount,

        @Schema(description = "Number of transactions skipped (duplicates)", example = "5")
        int skippedCount,

        @Schema(description = "Number of transactions that failed to import", example = "2")
        int errorCount,

        @Schema(description = "List of error messages for failed imports")
        List<ImportError> errors
) {
    /**
     * Represents an error that occurred during import of a specific item.
     */
    @Schema(description = "Error details for a failed import item")
    public record ImportError(
            @Schema(description = "Zero-based index of the item in the request", example = "3")
            int index,

            @Schema(description = "Error message describing why the import failed", example = "Invalid date format")
            String message
    ) {
    }
}
