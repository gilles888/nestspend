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
import be.gilmotech.nestspend.dto.transaction.TransactionCreateRequest;
import be.gilmotech.nestspend.dto.transaction.TransactionResponse;
import be.gilmotech.nestspend.dto.transaction.TransactionUpdateRequest;
import be.gilmotech.nestspend.exception.ResourceNotFoundException;
import be.gilmotech.nestspend.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
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
}
