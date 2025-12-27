package be.gilmotech.nestspend.service;

import be.gilmotech.nestspend.domain.entity.Account;
import be.gilmotech.nestspend.domain.repository.AccountRepository;
import be.gilmotech.nestspend.domain.repository.HouseholdRepository;
import be.gilmotech.nestspend.dto.account.AccountCreateRequest;
import be.gilmotech.nestspend.dto.account.AccountResponse;
import be.gilmotech.nestspend.dto.account.AccountUpdateRequest;
import be.gilmotech.nestspend.exception.ResourceConflictException;
import be.gilmotech.nestspend.exception.ResourceNotFoundException;
import be.gilmotech.nestspend.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for account-related operations with household scoping.
 */
@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final HouseholdRepository householdRepository;
    private final CurrentUserService currentUserService;

    public AccountService(AccountRepository accountRepository,
                          HouseholdRepository householdRepository,
                          CurrentUserService currentUserService) {
        this.accountRepository = accountRepository;
        this.householdRepository = householdRepository;
        this.currentUserService = currentUserService;
    }

    /**
     * Get all accounts for the current user's household.
     *
     * @return list of account responses
     */
    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        return accountRepository.findByHouseholdId(householdId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Get an account by ID for the current user's household.
     *
     * @param id the account ID
     * @return account response
     * @throws ResourceNotFoundException if account not found or belongs to different household
     */
    @Transactional(readOnly = true)
    public AccountResponse getAccountById(UUID id) {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        Account account = accountRepository.findByIdAndHouseholdId(id, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        return toResponse(account);
    }

    /**
     * Create a new account for the current user's household.
     *
     * @param request the create request
     * @return created account response
     * @throws ResourceConflictException if account name already exists in household
     */
    @Transactional
    public AccountResponse createAccount(AccountCreateRequest request) {
        UUID householdId = currentUserService.getCurrentHouseholdId();

        // Check for unique name within household
        if (accountRepository.existsByHouseholdIdAndName(householdId, request.name())) {
            throw new ResourceConflictException("Account with this name already exists in household");
        }

        Account account = Account.builder()
                .household(householdRepository.getReferenceById(householdId))
                .name(request.name())
                .type(request.type())
                .build();

        account = accountRepository.save(account);
        return toResponse(account);
    }

    /**
     * Update an existing account for the current user's household.
     *
     * @param id      the account ID
     * @param request the update request
     * @return updated account response
     * @throws ResourceNotFoundException if account not found or belongs to different household
     * @throws ResourceConflictException if account name already exists in household
     */
    @Transactional
    public AccountResponse updateAccount(UUID id, AccountUpdateRequest request) {
        UUID householdId = currentUserService.getCurrentHouseholdId();

        Account account = accountRepository.findByIdAndHouseholdId(id, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        // Check for unique name within household (excluding current account)
        if (accountRepository.existsByHouseholdIdAndNameAndIdNot(householdId, request.name(), id)) {
            throw new ResourceConflictException("Account with this name already exists in household");
        }

        account.setName(request.name());
        account.setType(request.type());

        account = accountRepository.save(account);
        return toResponse(account);
    }

    /**
     * Delete an account by ID for the current user's household.
     *
     * @param id the account ID
     * @throws ResourceNotFoundException if account not found or belongs to different household
     */
    @Transactional
    public void deleteAccount(UUID id) {
        UUID householdId = currentUserService.getCurrentHouseholdId();

        Account account = accountRepository.findByIdAndHouseholdId(id, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        accountRepository.delete(account);
    }

    private AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getType(),
                account.getCreatedAt()
        );
    }
}
