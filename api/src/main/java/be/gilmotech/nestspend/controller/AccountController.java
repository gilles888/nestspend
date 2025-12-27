package be.gilmotech.nestspend.controller;

import be.gilmotech.nestspend.dto.account.AccountCreateRequest;
import be.gilmotech.nestspend.dto.account.AccountResponse;
import be.gilmotech.nestspend.dto.account.AccountUpdateRequest;
import be.gilmotech.nestspend.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Accounts", description = "Account management endpoints")
@SecurityRequirement(name = "bearer-jwt")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    @Operation(summary = "Get all accounts", description = "Returns all accounts for the current user's household")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved accounts"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token")
    })
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        List<AccountResponse> accounts = accountService.getAllAccounts();
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get account by ID", description = "Returns an account by its ID for the current user's household")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved account"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
            @ApiResponse(responseCode = "404", description = "Not Found - Account not found or belongs to different household")
    })
    public ResponseEntity<AccountResponse> getAccountById(@PathVariable UUID id) {
        AccountResponse account = accountService.getAccountById(id);
        return ResponseEntity.ok(account);
    }

    @PostMapping
    @Operation(summary = "Create a new account", description = "Creates a new account for the current user's household")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Successfully created account"),
            @ApiResponse(responseCode = "400", description = "Validation error - Invalid input data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
            @ApiResponse(responseCode = "409", description = "Conflict - Account with this name already exists in household")
    })
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody AccountCreateRequest request) {
        AccountResponse account = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(account);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an account", description = "Updates an existing account for the current user's household")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully updated account"),
            @ApiResponse(responseCode = "400", description = "Validation error - Invalid input data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
            @ApiResponse(responseCode = "404", description = "Not Found - Account not found or belongs to different household"),
            @ApiResponse(responseCode = "409", description = "Conflict - Account with this name already exists in household")
    })
    public ResponseEntity<AccountResponse> updateAccount(
            @PathVariable UUID id,
            @Valid @RequestBody AccountUpdateRequest request) {
        AccountResponse account = accountService.updateAccount(id, request);
        return ResponseEntity.ok(account);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an account", description = "Deletes an account for the current user's household")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Successfully deleted account"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
            @ApiResponse(responseCode = "404", description = "Not Found - Account not found or belongs to different household")
    })
    public ResponseEntity<Void> deleteAccount(@PathVariable UUID id) {
        accountService.deleteAccount(id);
        return ResponseEntity.noContent().build();
    }
}
