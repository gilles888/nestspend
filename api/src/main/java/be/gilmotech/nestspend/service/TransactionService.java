package be.gilmotech.nestspend.service;

import be.gilmotech.nestspend.domain.entity.Account;
import be.gilmotech.nestspend.domain.entity.Category;
import be.gilmotech.nestspend.domain.entity.Transaction;
import be.gilmotech.nestspend.domain.entity.User;
import be.gilmotech.nestspend.domain.enums.TransactionType;
import be.gilmotech.nestspend.domain.repository.AccountRepository;
import be.gilmotech.nestspend.domain.repository.CategoryRepository;
import be.gilmotech.nestspend.domain.repository.TransactionRepository;
import be.gilmotech.nestspend.domain.repository.UserRepository;
import be.gilmotech.nestspend.dto.transaction.ImportCheckRequest;
import be.gilmotech.nestspend.dto.transaction.ImportCheckResponse;
import be.gilmotech.nestspend.dto.transaction.ImportTransactionItem;
import be.gilmotech.nestspend.dto.transaction.ImportTransactionRequest;
import be.gilmotech.nestspend.dto.transaction.ImportTransactionResponse;
import be.gilmotech.nestspend.dto.transaction.TransactionCreateRequest;
import be.gilmotech.nestspend.dto.transaction.TransactionResponse;
import be.gilmotech.nestspend.dto.transaction.TransactionUpdateRequest;
import be.gilmotech.nestspend.exception.ResourceNotFoundException;
import be.gilmotech.nestspend.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service for transaction-related operations with household scoping.
 */
@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    public TransactionService(TransactionRepository transactionRepository,
                              CategoryRepository categoryRepository,
                              AccountRepository accountRepository,
                              UserRepository userRepository,
                              CurrentUserService currentUserService) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    /**
     * Get all transactions for the current user's household with optional filters.
     *
     * @param fromDate   optional start date filter (inclusive)
     * @param toDate     optional end date filter (inclusive)
     * @param categoryId optional category ID filter
     * @param accountId  optional account ID filter
     * @param type       optional transaction type filter
     * @return list of transaction responses
     */
    @Transactional(readOnly = true)
    public List<TransactionResponse> getAllTransactions(LocalDate fromDate,
                                                        LocalDate toDate,
                                                        UUID categoryId,
                                                        UUID accountId,
                                                        TransactionType type) {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        return transactionRepository.findByHouseholdIdWithFilters(
                householdId, fromDate, toDate, categoryId, accountId, type
        ).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Get a transaction by ID for the current user's household.
     *
     * @param id the transaction ID
     * @return transaction response
     * @throws ResourceNotFoundException if transaction not found or belongs to different household
     */
    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(UUID id) {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        Transaction transaction = transactionRepository.findByIdAndHouseholdId(id, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        return toResponse(transaction);
    }

    /**
     * Create a new transaction for the current user's household.
     *
     * @param request the create request
     * @return created transaction response
     * @throws ResourceNotFoundException if category or account not found or belongs to different household
     */
    @Transactional
    public TransactionResponse createTransaction(TransactionCreateRequest request) {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        UUID userId = currentUserService.getCurrentUserId();

        // Validate category ownership
        Category category = categoryRepository.findByIdAndHouseholdId(request.categoryId(), householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        // Validate account ownership
        Account account = accountRepository.findByIdAndHouseholdId(request.accountId(), householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        // Get current user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Transaction transaction = Transaction.builder()
                .household(category.getHousehold())
                .createdBy(user)
                .txDate(request.txDate())
                .type(request.type())
                .amountCents(request.amountCents())
                .category(category)
                .account(account)
                .merchant(request.merchant())
                .note(request.note())
                .build();

        transaction = transactionRepository.save(transaction);
        return toResponse(transaction);
    }

    /**
     * Update an existing transaction for the current user's household.
     *
     * @param id      the transaction ID
     * @param request the update request
     * @return updated transaction response
     * @throws ResourceNotFoundException if transaction, category, or account not found or belongs to different household
     */
    @Transactional
    public TransactionResponse updateTransaction(UUID id, TransactionUpdateRequest request) {
        UUID householdId = currentUserService.getCurrentHouseholdId();

        Transaction transaction = transactionRepository.findByIdAndHouseholdId(id, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        // Validate category ownership
        Category category = categoryRepository.findByIdAndHouseholdId(request.categoryId(), householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        // Validate account ownership
        Account account = accountRepository.findByIdAndHouseholdId(request.accountId(), householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        transaction.setTxDate(request.txDate());
        transaction.setType(request.type());
        transaction.setAmountCents(request.amountCents());
        transaction.setCategory(category);
        transaction.setAccount(account);
        transaction.setMerchant(request.merchant());
        transaction.setNote(request.note());

        transaction = transactionRepository.save(transaction);
        return toResponse(transaction);
    }

    /**
     * Delete a transaction by ID for the current user's household.
     *
     * @param id the transaction ID
     * @throws ResourceNotFoundException if transaction not found or belongs to different household
     */
    @Transactional
    public void deleteTransaction(UUID id) {
        UUID householdId = currentUserService.getCurrentHouseholdId();

        Transaction transaction = transactionRepository.findByIdAndHouseholdId(id, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        transactionRepository.delete(transaction);
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getTxDate(),
                transaction.getType(),
                transaction.getAmountCents(),
                transaction.getCategory().getId(),
                transaction.getCategory().getName(),
                transaction.getAccount().getId(),
                transaction.getAccount().getName(),
                transaction.getMerchant(),
                transaction.getNote(),
                transaction.getCreatedBy() != null ? transaction.getCreatedBy().getId() : null,
                transaction.getCreatedBy() != null ? transaction.getCreatedBy().getDisplayName() : null,
                transaction.getCreatedAt(),
                transaction.getUpdatedAt()
        );
    }

    /**
     * Bulk import transactions for the current user's household.
     * Performs deduplication based on date, amount, and merchant.
     *
     * @param request the import request containing account ID and items to import
     * @return import response with counts of created, skipped, and errored transactions
     * @throws ResourceNotFoundException if account not found or belongs to different household
     */
    @Transactional
    public ImportTransactionResponse bulkImport(ImportTransactionRequest request) {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        UUID userId = currentUserService.getCurrentUserId();

        // Validate account ownership
        Account account = accountRepository.findByIdAndHouseholdId(request.accountId(), householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        // Get current user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Find default/uncategorized category or first available
        Category defaultCategory = categoryRepository.findByHouseholdIdAndNameIgnoreCase(householdId, "Uncategorized")
                .orElseGet(() -> categoryRepository.findFirstByHouseholdId(householdId)
                        .orElseThrow(() -> new ResourceNotFoundException("No categories found. Please create at least one category.")));

        // Calculate date range for deduplication
        LocalDate minDate = request.items().stream()
                .map(ImportTransactionItem::txDate)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());
        LocalDate maxDate = request.items().stream()
                .map(ImportTransactionItem::txDate)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());

        // Get existing transactions for deduplication
        List<Transaction> existingTransactions = transactionRepository
                .findByAccountIdAndHouseholdIdAndDateRange(request.accountId(), householdId, minDate, maxDate);
        Set<String> existingKeys = new HashSet<>();
        for (Transaction tx : existingTransactions) {
            existingKeys.add(buildDeduplicationKey(tx.getTxDate(), tx.getAmountCents(), tx.getMerchant()));
        }

        int createdCount = 0;
        int skippedCount = 0;
        List<ImportTransactionResponse.ImportError> errors = new ArrayList<>();

        for (int i = 0; i < request.items().size(); i++) {
            ImportTransactionItem item = request.items().get(i);
            try {
                String key = buildDeduplicationKey(item.txDate(), item.amountCents(), item.merchant());

                // Skip if duplicate
                if (existingKeys.contains(key)) {
                    skippedCount++;
                    continue;
                }

                // Use provided category or default
                Category category = defaultCategory;
                if (item.categoryId() != null) {
                    category = categoryRepository.findByIdAndHouseholdId(item.categoryId(), householdId)
                            .orElse(defaultCategory);
                }

                // Create transaction
                Transaction transaction = Transaction.builder()
                        .household(account.getHousehold())
                        .createdBy(user)
                        .txDate(item.txDate())
                        .type(item.type())
                        .amountCents(item.amountCents())
                        .category(category)
                        .account(account)
                        .merchant(item.merchant())
                        .note(item.note())
                        .build();

                transactionRepository.save(transaction);
                existingKeys.add(key); // Add to set to avoid duplicates within same import
                createdCount++;
            } catch (Exception e) {
                errors.add(new ImportTransactionResponse.ImportError(i, e.getMessage()));
            }
        }

        return new ImportTransactionResponse(createdCount, skippedCount, errors.size(), errors);
    }

    /**
     * Check which transactions already exist in the database for deduplication.
     *
     * @param request the check request with account ID, date range, and keys to check
     * @return response containing keys that already exist
     * @throws ResourceNotFoundException if account not found or belongs to different household
     */
    @Transactional(readOnly = true)
    public ImportCheckResponse checkExisting(ImportCheckRequest request) {
        UUID householdId = currentUserService.getCurrentHouseholdId();

        // Validate account ownership
        accountRepository.findByIdAndHouseholdId(request.accountId(), householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        // Get existing transactions
        List<Transaction> existingTransactions = transactionRepository
                .findByAccountIdAndHouseholdIdAndDateRange(request.accountId(), householdId, request.from(), request.to());

        // Build set of existing keys
        Set<String> existingKeys = new HashSet<>();
        for (Transaction tx : existingTransactions) {
            existingKeys.add(buildDeduplicationKey(tx.getTxDate(), tx.getAmountCents(), tx.getMerchant()));
        }

        // Return keys that exist in both sets
        List<String> matchingKeys = request.keys().stream()
                .filter(existingKeys::contains)
                .toList();

        return new ImportCheckResponse(matchingKeys);
    }

    /**
     * Build a deduplication key from transaction properties.
     * Format: DATE|AMOUNT|MERCHANT (merchant is lowercased and trimmed)
     */
    private String buildDeduplicationKey(LocalDate date, Long amountCents, String merchant) {
        String normalizedMerchant = merchant != null ? merchant.toLowerCase().trim() : "";
        return date.toString() + "|" + amountCents + "|" + normalizedMerchant;
    }
}
