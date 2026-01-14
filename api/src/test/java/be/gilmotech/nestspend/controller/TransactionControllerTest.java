package be.gilmotech.nestspend.controller;

import be.gilmotech.nestspend.domain.entity.Account;
import be.gilmotech.nestspend.domain.entity.Category;
import be.gilmotech.nestspend.domain.entity.Household;
import be.gilmotech.nestspend.domain.entity.Transaction;
import be.gilmotech.nestspend.domain.entity.User;
import be.gilmotech.nestspend.domain.enums.AccountType;
import be.gilmotech.nestspend.domain.enums.TransactionType;
import be.gilmotech.nestspend.domain.enums.UserRole;
import be.gilmotech.nestspend.domain.repository.AccountRepository;
import be.gilmotech.nestspend.domain.repository.CategoryRepository;
import be.gilmotech.nestspend.domain.repository.ClassificationRuleRepository;
import be.gilmotech.nestspend.domain.repository.HouseholdRepository;
import be.gilmotech.nestspend.domain.repository.TransactionRepository;
import be.gilmotech.nestspend.domain.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ClassificationRuleRepository classificationRuleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        classificationRuleRepository.deleteAll();
        categoryRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
        householdRepository.deleteAll();
    }

    private String getTokenForUser(String email, String password) throws Exception {
        String loginRequest = String.format("""
                {
                    "email": "%s",
                    "password": "%s"
                }
                """, email, password);

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("token").asText();
    }

    private Household createHousehold(String name) {
        return householdRepository.save(Household.builder()
                .name(name)
                .build());
    }

    private User createUser(Household household, String email, String password) {
        return userRepository.save(User.builder()
                .household(household)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .displayName("Test User")
                .role(UserRole.ADMIN)
                .build());
    }

    private Category createCategory(Household household, String name) {
        return categoryRepository.save(Category.builder()
                .household(household)
                .name(name)
                .color("#FF0000")
                .icon("shopping")
                .build());
    }

    private Account createAccount(Household household, String name) {
        return accountRepository.save(Account.builder()
                .household(household)
                .name(name)
                .type(AccountType.BANK)
                .build());
    }

    private Transaction createTransaction(Household household, User user, Category category, Account account,
                                          LocalDate txDate, TransactionType type, Long amountCents) {
        return transactionRepository.save(Transaction.builder()
                .household(household)
                .createdBy(user)
                .category(category)
                .account(account)
                .txDate(txDate)
                .type(type)
                .amountCents(amountCents)
                .merchant("Test Merchant")
                .note("Test Note")
                .build());
    }

    // ==================== GET ALL TRANSACTIONS TESTS ====================

    @Test
    void getAllTransactions_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAllTransactions_withValidToken_shouldReturnTransactions() throws Exception {
        Household household = createHousehold("Test Household");
        User user = createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Food");
        Account account = createAccount(household, "Main Account");
        createTransaction(household, user, category, account, LocalDate.of(2024, 1, 15), TransactionType.EXPENSE, 1500L);
        createTransaction(household, user, category, account, LocalDate.of(2024, 1, 16), TransactionType.INCOME, 3000L);

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].type", containsInAnyOrder("EXPENSE", "INCOME")));
    }

    @Test
    void getAllTransactions_shouldOnlyReturnTransactionsFromOwnHousehold() throws Exception {
        Household household1 = createHousehold("Household 1");
        User user1 = createUser(household1, "user1@example.com", "password123");
        Category category1 = createCategory(household1, "Food");
        Account account1 = createAccount(household1, "Account 1");
        createTransaction(household1, user1, category1, account1, LocalDate.of(2024, 1, 15), TransactionType.EXPENSE, 1500L);

        Household household2 = createHousehold("Household 2");
        User user2 = createUser(household2, "user2@example.com", "password123");
        Category category2 = createCategory(household2, "Transport");
        Account account2 = createAccount(household2, "Account 2");
        createTransaction(household2, user2, category2, account2, LocalDate.of(2024, 1, 16), TransactionType.EXPENSE, 2500L);
        createTransaction(household2, user2, category2, account2, LocalDate.of(2024, 1, 17), TransactionType.INCOME, 5000L);

        String token1 = getTokenForUser("user1@example.com", "password123");

        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].amountCents").value(1500));
    }

    // ==================== FILTER TESTS ====================

    @Test
    void getAllTransactions_filterByDateRange_shouldReturnMatchingTransactions() throws Exception {
        Household household = createHousehold("Test Household");
        User user = createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Food");
        Account account = createAccount(household, "Main Account");
        createTransaction(household, user, category, account, LocalDate.of(2024, 1, 1), TransactionType.EXPENSE, 1000L);
        createTransaction(household, user, category, account, LocalDate.of(2024, 1, 15), TransactionType.EXPENSE, 1500L);
        createTransaction(household, user, category, account, LocalDate.of(2024, 2, 1), TransactionType.EXPENSE, 2000L);

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2024-01-10")
                        .param("to", "2024-01-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].amountCents").value(1500));
    }

    @Test
    void getAllTransactions_filterByCategory_shouldReturnMatchingTransactions() throws Exception {
        Household household = createHousehold("Test Household");
        User user = createUser(household, "user@example.com", "password123");
        Category foodCategory = createCategory(household, "Food");
        Category transportCategory = createCategory(household, "Transport");
        Account account = createAccount(household, "Main Account");
        createTransaction(household, user, foodCategory, account, LocalDate.of(2024, 1, 15), TransactionType.EXPENSE, 1500L);
        createTransaction(household, user, transportCategory, account, LocalDate.of(2024, 1, 16), TransactionType.EXPENSE, 2500L);

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .param("categoryId", foodCategory.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].categoryName").value("Food"));
    }

    @Test
    void getAllTransactions_filterByAccount_shouldReturnMatchingTransactions() throws Exception {
        Household household = createHousehold("Test Household");
        User user = createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Food");
        Account mainAccount = createAccount(household, "Main Account");
        Account savingsAccount = createAccount(household, "Savings Account");
        createTransaction(household, user, category, mainAccount, LocalDate.of(2024, 1, 15), TransactionType.EXPENSE, 1500L);
        createTransaction(household, user, category, savingsAccount, LocalDate.of(2024, 1, 16), TransactionType.EXPENSE, 2500L);

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .param("accountId", mainAccount.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].accountName").value("Main Account"));
    }

    @Test
    void getAllTransactions_filterByType_shouldReturnMatchingTransactions() throws Exception {
        Household household = createHousehold("Test Household");
        User user = createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Food");
        Account account = createAccount(household, "Main Account");
        createTransaction(household, user, category, account, LocalDate.of(2024, 1, 15), TransactionType.EXPENSE, 1500L);
        createTransaction(household, user, category, account, LocalDate.of(2024, 1, 16), TransactionType.INCOME, 3000L);

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .param("type", "INCOME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("INCOME"));
    }

    @Test
    void getAllTransactions_combinedFilters_shouldReturnMatchingTransactions() throws Exception {
        Household household = createHousehold("Test Household");
        User user = createUser(household, "user@example.com", "password123");
        Category foodCategory = createCategory(household, "Food");
        Category transportCategory = createCategory(household, "Transport");
        Account mainAccount = createAccount(household, "Main Account");
        Account savingsAccount = createAccount(household, "Savings Account");

        // Transaction that matches all filters
        createTransaction(household, user, foodCategory, mainAccount, LocalDate.of(2024, 1, 15), TransactionType.EXPENSE, 1500L);
        // Different category
        createTransaction(household, user, transportCategory, mainAccount, LocalDate.of(2024, 1, 15), TransactionType.EXPENSE, 2000L);
        // Different account
        createTransaction(household, user, foodCategory, savingsAccount, LocalDate.of(2024, 1, 15), TransactionType.EXPENSE, 2500L);
        // Different type
        createTransaction(household, user, foodCategory, mainAccount, LocalDate.of(2024, 1, 15), TransactionType.INCOME, 3000L);
        // Outside date range
        createTransaction(household, user, foodCategory, mainAccount, LocalDate.of(2024, 2, 15), TransactionType.EXPENSE, 3500L);

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2024-01-01")
                        .param("to", "2024-01-31")
                        .param("categoryId", foodCategory.getId().toString())
                        .param("accountId", mainAccount.getId().toString())
                        .param("type", "EXPENSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].amountCents").value(1500));
    }

    // ==================== GET BY ID TESTS ====================

    @Test
    void getTransactionById_shouldReturnTransaction() throws Exception {
        Household household = createHousehold("Test Household");
        User user = createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Food");
        Account account = createAccount(household, "Main Account");
        Transaction transaction = createTransaction(household, user, category, account,
                LocalDate.of(2024, 1, 15), TransactionType.EXPENSE, 1500L);

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(get("/api/transactions/" + transaction.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transaction.getId().toString()))
                .andExpect(jsonPath("$.amountCents").value(1500))
                .andExpect(jsonPath("$.categoryName").value("Food"))
                .andExpect(jsonPath("$.accountName").value("Main Account"))
                .andExpect(jsonPath("$.merchant").value("Test Merchant"))
                .andExpect(jsonPath("$.note").value("Test Note"));
    }

    @Test
    void getTransactionById_fromDifferentHousehold_shouldReturn404() throws Exception {
        Household household1 = createHousehold("Household 1");
        createUser(household1, "user1@example.com", "password123");

        Household household2 = createHousehold("Household 2");
        User user2 = createUser(household2, "user2@example.com", "password123");
        Category category2 = createCategory(household2, "Food");
        Account account2 = createAccount(household2, "Account 2");
        Transaction transactionFromHousehold2 = createTransaction(household2, user2, category2, account2,
                LocalDate.of(2024, 1, 15), TransactionType.EXPENSE, 1500L);

        String token1 = getTokenForUser("user1@example.com", "password123");

        mockMvc.perform(get("/api/transactions/" + transactionFromHousehold2.getId())
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transaction not found"));
    }

    // ==================== CREATE TESTS ====================

    @Test
    void createTransaction_shouldCreateAndReturnTransaction() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Food");
        Account account = createAccount(household, "Main Account");

        String token = getTokenForUser("user@example.com", "password123");

        String request = String.format("""
                {
                    "txDate": "2024-01-15",
                    "type": "EXPENSE",
                    "amountCents": 1500,
                    "categoryId": "%s",
                    "accountId": "%s",
                    "merchant": "Supermarket",
                    "note": "Weekly groceries"
                }
                """, category.getId(), account.getId());

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.txDate").value("2024-01-15"))
                .andExpect(jsonPath("$.type").value("EXPENSE"))
                .andExpect(jsonPath("$.amountCents").value(1500))
                .andExpect(jsonPath("$.categoryId").value(category.getId().toString()))
                .andExpect(jsonPath("$.categoryName").value("Food"))
                .andExpect(jsonPath("$.accountId").value(account.getId().toString()))
                .andExpect(jsonPath("$.accountName").value("Main Account"))
                .andExpect(jsonPath("$.merchant").value("Supermarket"))
                .andExpect(jsonPath("$.note").value("Weekly groceries"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.createdById").isNotEmpty())
                .andExpect(jsonPath("$.createdByName").value("Test User"));
    }

    @Test
    void createTransaction_withCategoryFromDifferentHousehold_shouldReturn404() throws Exception {
        Household household1 = createHousehold("Household 1");
        createUser(household1, "user1@example.com", "password123");
        Account account1 = createAccount(household1, "Account 1");

        Household household2 = createHousehold("Household 2");
        createUser(household2, "user2@example.com", "password123");
        Category category2 = createCategory(household2, "Food from H2");

        String token1 = getTokenForUser("user1@example.com", "password123");

        String request = String.format("""
                {
                    "txDate": "2024-01-15",
                    "type": "EXPENSE",
                    "amountCents": 1500,
                    "categoryId": "%s",
                    "accountId": "%s"
                }
                """, category2.getId(), account1.getId());

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Category not found"));
    }

    @Test
    void createTransaction_withAccountFromDifferentHousehold_shouldReturn404() throws Exception {
        Household household1 = createHousehold("Household 1");
        createUser(household1, "user1@example.com", "password123");
        Category category1 = createCategory(household1, "Food");

        Household household2 = createHousehold("Household 2");
        createUser(household2, "user2@example.com", "password123");
        Account account2 = createAccount(household2, "Account from H2");

        String token1 = getTokenForUser("user1@example.com", "password123");

        String request = String.format("""
                {
                    "txDate": "2024-01-15",
                    "type": "EXPENSE",
                    "amountCents": 1500,
                    "categoryId": "%s",
                    "accountId": "%s"
                }
                """, category1.getId(), account2.getId());

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found"));
    }

    @Test
    void createTransaction_withMissingRequiredFields_shouldReturn400() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "merchant": "Supermarket"
                }
                """;

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.txDate").exists())
                .andExpect(jsonPath("$.errors.type").exists())
                .andExpect(jsonPath("$.errors.amountCents").exists())
                .andExpect(jsonPath("$.errors.categoryId").exists())
                .andExpect(jsonPath("$.errors.accountId").exists());
    }

    @Test
    void createTransaction_withNegativeAmount_shouldReturn400() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Food");
        Account account = createAccount(household, "Main Account");

        String token = getTokenForUser("user@example.com", "password123");

        String request = String.format("""
                {
                    "txDate": "2024-01-15",
                    "type": "EXPENSE",
                    "amountCents": -1500,
                    "categoryId": "%s",
                    "accountId": "%s"
                }
                """, category.getId(), account.getId());

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amountCents").exists());
    }

    @Test
    void createTransaction_withZeroAmount_shouldReturn400() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Food");
        Account account = createAccount(household, "Main Account");

        String token = getTokenForUser("user@example.com", "password123");

        String request = String.format("""
                {
                    "txDate": "2024-01-15",
                    "type": "EXPENSE",
                    "amountCents": 0,
                    "categoryId": "%s",
                    "accountId": "%s"
                }
                """, category.getId(), account.getId());

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amountCents").exists());
    }

    // ==================== UPDATE TESTS ====================

    @Test
    void updateTransaction_shouldUpdateAndReturnTransaction() throws Exception {
        Household household = createHousehold("Test Household");
        User user = createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Food");
        Category newCategory = createCategory(household, "Transport");
        Account account = createAccount(household, "Main Account");
        Transaction transaction = createTransaction(household, user, category, account,
                LocalDate.of(2024, 1, 15), TransactionType.EXPENSE, 1500L);

        String token = getTokenForUser("user@example.com", "password123");

        String request = String.format("""
                {
                    "txDate": "2024-01-20",
                    "type": "INCOME",
                    "amountCents": 3000,
                    "categoryId": "%s",
                    "accountId": "%s",
                    "merchant": "New Merchant",
                    "note": "Updated note"
                }
                """, newCategory.getId(), account.getId());

        mockMvc.perform(put("/api/transactions/" + transaction.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transaction.getId().toString()))
                .andExpect(jsonPath("$.txDate").value("2024-01-20"))
                .andExpect(jsonPath("$.type").value("INCOME"))
                .andExpect(jsonPath("$.amountCents").value(3000))
                .andExpect(jsonPath("$.categoryId").value(newCategory.getId().toString()))
                .andExpect(jsonPath("$.categoryName").value("Transport"))
                .andExpect(jsonPath("$.merchant").value("New Merchant"))
                .andExpect(jsonPath("$.note").value("Updated note"));
    }

    @Test
    void updateTransaction_fromDifferentHousehold_shouldReturn404() throws Exception {
        Household household1 = createHousehold("Household 1");
        createUser(household1, "user1@example.com", "password123");
        Category category1 = createCategory(household1, "Food");
        Account account1 = createAccount(household1, "Account 1");

        Household household2 = createHousehold("Household 2");
        User user2 = createUser(household2, "user2@example.com", "password123");
        Category category2 = createCategory(household2, "Food");
        Account account2 = createAccount(household2, "Account 2");
        Transaction transactionFromHousehold2 = createTransaction(household2, user2, category2, account2,
                LocalDate.of(2024, 1, 15), TransactionType.EXPENSE, 1500L);

        String token1 = getTokenForUser("user1@example.com", "password123");

        String request = String.format("""
                {
                    "txDate": "2024-01-20",
                    "type": "INCOME",
                    "amountCents": 3000,
                    "categoryId": "%s",
                    "accountId": "%s"
                }
                """, category1.getId(), account1.getId());

        mockMvc.perform(put("/api/transactions/" + transactionFromHousehold2.getId())
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transaction not found"));
    }

    @Test
    void updateTransaction_withCategoryFromDifferentHousehold_shouldReturn404() throws Exception {
        Household household1 = createHousehold("Household 1");
        User user1 = createUser(household1, "user1@example.com", "password123");
        Category category1 = createCategory(household1, "Food");
        Account account1 = createAccount(household1, "Account 1");
        Transaction transaction = createTransaction(household1, user1, category1, account1,
                LocalDate.of(2024, 1, 15), TransactionType.EXPENSE, 1500L);

        Household household2 = createHousehold("Household 2");
        createUser(household2, "user2@example.com", "password123");
        Category category2 = createCategory(household2, "Food from H2");

        String token1 = getTokenForUser("user1@example.com", "password123");

        String request = String.format("""
                {
                    "txDate": "2024-01-20",
                    "type": "INCOME",
                    "amountCents": 3000,
                    "categoryId": "%s",
                    "accountId": "%s"
                }
                """, category2.getId(), account1.getId());

        mockMvc.perform(put("/api/transactions/" + transaction.getId())
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Category not found"));
    }

    // ==================== DELETE TESTS ====================

    @Test
    void deleteTransaction_shouldDeleteTransaction() throws Exception {
        Household household = createHousehold("Test Household");
        User user = createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Food");
        Account account = createAccount(household, "Main Account");
        Transaction transaction = createTransaction(household, user, category, account,
                LocalDate.of(2024, 1, 15), TransactionType.EXPENSE, 1500L);

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(delete("/api/transactions/" + transaction.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Verify transaction is deleted
        mockMvc.perform(get("/api/transactions/" + transaction.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTransaction_fromDifferentHousehold_shouldReturn404() throws Exception {
        Household household1 = createHousehold("Household 1");
        createUser(household1, "user1@example.com", "password123");

        Household household2 = createHousehold("Household 2");
        User user2 = createUser(household2, "user2@example.com", "password123");
        Category category2 = createCategory(household2, "Food");
        Account account2 = createAccount(household2, "Account 2");
        Transaction transactionFromHousehold2 = createTransaction(household2, user2, category2, account2,
                LocalDate.of(2024, 1, 15), TransactionType.EXPENSE, 1500L);

        String token1 = getTokenForUser("user1@example.com", "password123");

        mockMvc.perform(delete("/api/transactions/" + transactionFromHousehold2.getId())
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transaction not found"));
    }

    // ==================== FULL CRUD FLOW TESTS ====================

    @Test
    void fullCrudFlow_shouldWorkCorrectly() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Food");
        Category newCategory = createCategory(household, "Transport");
        Account account = createAccount(household, "Main Account");

        String token = getTokenForUser("user@example.com", "password123");

        // Create
        String createRequest = String.format("""
                {
                    "txDate": "2024-01-15",
                    "type": "EXPENSE",
                    "amountCents": 1500,
                    "categoryId": "%s",
                    "accountId": "%s",
                    "merchant": "Supermarket",
                    "note": "Weekly groceries"
                }
                """, category.getId(), account.getId());

        String createResponse = mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String transactionId = objectMapper.readTree(createResponse).get("id").asText();

        // Read (list)
        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].amountCents").value(1500));

        // Read (single)
        mockMvc.perform(get("/api/transactions/" + transactionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountCents").value(1500));

        // Update
        String updateRequest = String.format("""
                {
                    "txDate": "2024-01-20",
                    "type": "INCOME",
                    "amountCents": 3000,
                    "categoryId": "%s",
                    "accountId": "%s",
                    "merchant": "Company",
                    "note": "Salary"
                }
                """, newCategory.getId(), account.getId());

        mockMvc.perform(put("/api/transactions/" + transactionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountCents").value(3000))
                .andExpect(jsonPath("$.type").value("INCOME"));

        // Delete
        mockMvc.perform(delete("/api/transactions/" + transactionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Verify deleted
        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void createThenListByDateRange_shouldWorkCorrectly() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Food");
        Account account = createAccount(household, "Main Account");

        String token = getTokenForUser("user@example.com", "password123");

        // Create transactions for different dates
        for (int day = 1; day <= 3; day++) {
            String request = String.format("""
                    {
                        "txDate": "2024-01-%02d",
                        "type": "EXPENSE",
                        "amountCents": %d,
                        "categoryId": "%s",
                        "accountId": "%s"
                    }
                    """, day, day * 100, category.getId(), account.getId());

            mockMvc.perform(post("/api/transactions")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isCreated());
        }

        // List all
        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        // List by date range
        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2024-01-02")
                        .param("to", "2024-01-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].amountCents").value(200));
    }
}
