package be.gilmotech.nestspend.controller;

import be.gilmotech.nestspend.domain.enums.TransactionType;
import be.gilmotech.nestspend.dto.transaction.ImportCheckRequest;
import be.gilmotech.nestspend.dto.transaction.ImportCheckResponse;
import be.gilmotech.nestspend.dto.transaction.ImportTransactionRequest;
import be.gilmotech.nestspend.dto.transaction.ImportTransactionResponse;
import be.gilmotech.nestspend.dto.transaction.TransactionCreateRequest;
import be.gilmotech.nestspend.dto.transaction.TransactionResponse;
import be.gilmotech.nestspend.dto.transaction.TransactionUpdateRequest;
import be.gilmotech.nestspend.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "Transaction management endpoints with filtering capabilities")
@SecurityRequirement(name = "bearer-jwt")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    @Operation(
            summary = "Get all transactions with optional filters",
            description = "Returns all transactions for the current user's household. " +
                    "Supports optional filtering by date range, category, account, and transaction type."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved transactions"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<List<TransactionResponse>> getAllTransactions(
            @Parameter(description = "Start date (inclusive) in YYYY-MM-DD format", example = "2024-01-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,

            @Parameter(description = "End date (inclusive) in YYYY-MM-DD format", example = "2024-12-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,

            @Parameter(description = "Filter by category UUID")
            @RequestParam(required = false) UUID categoryId,

            @Parameter(description = "Filter by account UUID")
            @RequestParam(required = false) UUID accountId,

            @Parameter(description = "Filter by transaction type (EXPENSE or INCOME)")
            @RequestParam(required = false) TransactionType type
    ) {
        List<TransactionResponse> transactions = transactionService.getAllTransactions(
                from, to, categoryId, accountId, type
        );
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get transaction by ID",
            description = "Returns a transaction by its ID for the current user's household"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved transaction"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Not Found - Transaction not found or belongs to different household",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<TransactionResponse> getTransactionById(@PathVariable UUID id) {
        TransactionResponse transaction = transactionService.getTransactionById(id);
        return ResponseEntity.ok(transaction);
    }

    @PostMapping
    @Operation(
            summary = "Create a new transaction",
            description = "Creates a new transaction for the current user's household. " +
                    "The category and account must belong to the same household."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Successfully created transaction"),
            @ApiResponse(responseCode = "400", description = "Validation error - Invalid input data (e.g., missing required fields, negative amount)",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Not Found - Category or account not found or belongs to different household",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<TransactionResponse> createTransaction(
            @Valid @RequestBody TransactionCreateRequest request) {
        TransactionResponse transaction = transactionService.createTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(transaction);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update a transaction",
            description = "Updates an existing transaction for the current user's household. " +
                    "The category and account must belong to the same household."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully updated transaction"),
            @ApiResponse(responseCode = "400", description = "Validation error - Invalid input data (e.g., missing required fields, negative amount)",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Not Found - Transaction, category, or account not found or belongs to different household",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<TransactionResponse> updateTransaction(
            @PathVariable UUID id,
            @Valid @RequestBody TransactionUpdateRequest request) {
        TransactionResponse transaction = transactionService.updateTransaction(id, request);
        return ResponseEntity.ok(transaction);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a transaction",
            description = "Deletes a transaction for the current user's household"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Successfully deleted transaction"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Not Found - Transaction not found or belongs to different household",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<Void> deleteTransaction(@PathVariable UUID id) {
        transactionService.deleteTransaction(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import")
    @Operation(
            summary = "Bulk import transactions",
            description = "Imports multiple transactions at once for the specified account. " +
                    "Automatically performs deduplication based on date, amount, and merchant. " +
                    "Transactions that already exist will be skipped."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Import completed - check response for details"),
            @ApiResponse(responseCode = "400", description = "Validation error - Invalid input data",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Not Found - Account not found or belongs to different household",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<ImportTransactionResponse> importTransactions(
            @Valid @RequestBody ImportTransactionRequest request) {
        ImportTransactionResponse response = transactionService.bulkImport(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/import/check")
    @Operation(
            summary = "Check for existing transactions",
            description = "Checks which transactions from the provided list already exist in the database. " +
                    "Used for deduplication before import. Keys are in format: DATE|AMOUNT|MERCHANT"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Check completed - returns existing keys"),
            @ApiResponse(responseCode = "400", description = "Validation error - Invalid input data",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Not Found - Account not found or belongs to different household",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<ImportCheckResponse> checkExisting(
            @Valid @RequestBody ImportCheckRequest request) {
        ImportCheckResponse response = transactionService.checkExisting(request);
        return ResponseEntity.ok(response);
    }
}
