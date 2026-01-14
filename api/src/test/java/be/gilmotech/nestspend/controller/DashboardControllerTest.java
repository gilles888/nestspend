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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardControllerTest {

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

    // ==================== AUTHORIZATION TESTS ====================

    @Test
    void getDashboard_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/dashboard")
                        .param("month", "2024-01"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== VALIDATION TESTS ====================

    @Test
    void getDashboard_withInvalidMonth_shouldReturn400() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + token)
                        .param("month", "invalid-month"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid month format")));
    }

    @Test
    void getDashboard_withMissingMonth_shouldReturn400() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    // ==================== EMPTY DATA TESTS ====================

    @Test
    void getDashboard_withNoTransactions_shouldReturnZeros() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2024-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2024-01"))
                .andExpect(jsonPath("$.totalIncomeCents").value(0))
                .andExpect(jsonPath("$.totalExpenseCents").value(0))
                .andExpect(jsonPath("$.netCents").value(0))
                .andExpect(jsonPath("$.expensesByCategory", hasSize(0)));
    }

    // ==================== AGGREGATION TESTS ====================

    @Test
    void getDashboard_shouldReturnCorrectAggregations() throws Exception {
        Household household = createHousehold("Test Household");
        User user = createUser(household, "user@example.com", "password123");
        Category foodCategory = createCategory(household, "Food");
        Category transportCategory = createCategory(household, "Transport");
        Account account = createAccount(household, "Main Account");

        // Create expenses in January 2024
        createTransaction(household, user, foodCategory, account,
                LocalDate.of(2024, 1, 10), TransactionType.EXPENSE, 1500L);
        createTransaction(household, user, foodCategory, account,
                LocalDate.of(2024, 1, 15), TransactionType.EXPENSE, 2500L);
        createTransaction(household, user, transportCategory, account,
                LocalDate.of(2024, 1, 20), TransactionType.EXPENSE, 1000L);

        // Create income in January 2024
        createTransaction(household, user, foodCategory, account,
                LocalDate.of(2024, 1, 1), TransactionType.INCOME, 10000L);

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2024-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2024-01"))
                .andExpect(jsonPath("$.totalIncomeCents").value(10000))
                .andExpect(jsonPath("$.totalExpenseCents").value(5000))
                .andExpect(jsonPath("$.netCents").value(5000))
                .andExpect(jsonPath("$.expensesByCategory", hasSize(2)))
                // Food: 1500 + 2500 = 4000 (should be first as highest)
                .andExpect(jsonPath("$.expensesByCategory[0].categoryName").value("Food"))
                .andExpect(jsonPath("$.expensesByCategory[0].amountCents").value(4000))
                // Transport: 1000
                .andExpect(jsonPath("$.expensesByCategory[1].categoryName").value("Transport"))
                .andExpect(jsonPath("$.expensesByCategory[1].amountCents").value(1000));
    }

    @Test
    void getDashboard_shouldOnlyIncludeTransactionsFromRequestedMonth() throws Exception {
        Household household = createHousehold("Test Household");
        User user = createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Food");
        Account account = createAccount(household, "Main Account");

        // Transaction in December 2023 (should not be included)
        createTransaction(household, user, category, account,
                LocalDate.of(2023, 12, 31), TransactionType.EXPENSE, 5000L);

        // Transactions in January 2024 (should be included)
        createTransaction(household, user, category, account,
                LocalDate.of(2024, 1, 1), TransactionType.EXPENSE, 1000L);
        createTransaction(household, user, category, account,
                LocalDate.of(2024, 1, 31), TransactionType.EXPENSE, 2000L);

        // Transaction in February 2024 (should not be included)
        createTransaction(household, user, category, account,
                LocalDate.of(2024, 2, 1), TransactionType.EXPENSE, 3000L);

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2024-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExpenseCents").value(3000)) // 1000 + 2000
                .andExpect(jsonPath("$.expensesByCategory[0].amountCents").value(3000));
    }

    // ==================== HOUSEHOLD SCOPING TESTS ====================

    @Test
    void getDashboard_shouldOnlyIncludeTransactionsFromOwnHousehold() throws Exception {
        // Household 1
        Household household1 = createHousehold("Household 1");
        User user1 = createUser(household1, "user1@example.com", "password123");
        Category category1 = createCategory(household1, "Food");
        Account account1 = createAccount(household1, "Account 1");
        createTransaction(household1, user1, category1, account1,
                LocalDate.of(2024, 1, 15), TransactionType.EXPENSE, 1500L);

        // Household 2
        Household household2 = createHousehold("Household 2");
        User user2 = createUser(household2, "user2@example.com", "password123");
        Category category2 = createCategory(household2, "Food");
        Account account2 = createAccount(household2, "Account 2");
        createTransaction(household2, user2, category2, account2,
                LocalDate.of(2024, 1, 15), TransactionType.EXPENSE, 9999L);

        String token1 = getTokenForUser("user1@example.com", "password123");

        // User 1 should only see their own household's expenses
        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + token1)
                        .param("month", "2024-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExpenseCents").value(1500))
                .andExpect(jsonPath("$.expensesByCategory[0].amountCents").value(1500));
    }

    // ==================== NET CALCULATION TESTS ====================

    @Test
    void getDashboard_shouldCalculateNegativeNet() throws Exception {
        Household household = createHousehold("Test Household");
        User user = createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Food");
        Account account = createAccount(household, "Main Account");

        // More expenses than income
        createTransaction(household, user, category, account,
                LocalDate.of(2024, 1, 10), TransactionType.EXPENSE, 10000L);
        createTransaction(household, user, category, account,
                LocalDate.of(2024, 1, 1), TransactionType.INCOME, 3000L);

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2024-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncomeCents").value(3000))
                .andExpect(jsonPath("$.totalExpenseCents").value(10000))
                .andExpect(jsonPath("$.netCents").value(-7000));
    }

    // ==================== CATEGORY ID TESTS ====================

    @Test
    void getDashboard_shouldIncludeCategoryIdInExpensesByCategory() throws Exception {
        Household household = createHousehold("Test Household");
        User user = createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Food");
        Account account = createAccount(household, "Main Account");

        createTransaction(household, user, category, account,
                LocalDate.of(2024, 1, 10), TransactionType.EXPENSE, 1500L);

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2024-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expensesByCategory[0].categoryId").value(category.getId().toString()))
                .andExpect(jsonPath("$.expensesByCategory[0].categoryName").value("Food"))
                .andExpect(jsonPath("$.expensesByCategory[0].amountCents").value(1500));
    }
}
