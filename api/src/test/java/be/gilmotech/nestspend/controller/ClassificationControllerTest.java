package be.gilmotech.nestspend.controller;

import be.gilmotech.nestspend.domain.entity.Account;
import be.gilmotech.nestspend.domain.entity.Category;
import be.gilmotech.nestspend.domain.entity.ClassificationRule;
import be.gilmotech.nestspend.domain.entity.Household;
import be.gilmotech.nestspend.domain.entity.Transaction;
import be.gilmotech.nestspend.domain.entity.User;
import be.gilmotech.nestspend.domain.enums.MatchType;
import be.gilmotech.nestspend.domain.enums.RuleField;
import be.gilmotech.nestspend.domain.enums.RuleSource;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClassificationControllerTest {

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
    private ClassificationRuleRepository ruleRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        ruleRepository.deleteAll();
        categoryRepository.deleteAll();
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

    private ClassificationRule createRule(Household household, Category category, 
            RuleField field, MatchType matchType, String pattern, int priority, int confidence) {
        return ruleRepository.save(ClassificationRule.builder()
                .household(household)
                .category(category)
                .field(field)
                .matchType(matchType)
                .pattern(pattern)
                .enabled(true)
                .priority(priority)
                .confidence(confidence)
                .source(RuleSource.USER)
                .build());
    }

    @Test
    void suggest_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/classification/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "transactions": []
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void suggest_withNoRules_shouldReturnNoMatch() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "transactions": [
                        {
                            "merchant": "DELHAIZE",
                            "communication": "CB 12345",
                            "iban": "BE92123456789012",
                            "amount": 12345,
                            "date": "2026-01-01"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/classification/suggest")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions", hasSize(1)))
                .andExpect(jsonPath("$.suggestions[0].categoryId").doesNotExist())
                .andExpect(jsonPath("$.suggestions[0].confidence").value(0))
                .andExpect(jsonPath("$.suggestions[0].confidenceLabel").value("LOW"));
    }

    @Test
    void suggest_withMatchingContainsRule_shouldReturnSuggestion() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Alimentation");
        createRule(household, category, RuleField.MERCHANT, MatchType.CONTAINS, "DELHAIZE", 100, 80);

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "transactions": [
                        {
                            "merchant": "DELHAIZE CITY",
                            "communication": "CB 12345",
                            "iban": "BE92123456789012",
                            "amount": 12345,
                            "date": "2026-01-01"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/classification/suggest")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions", hasSize(1)))
                .andExpect(jsonPath("$.suggestions[0].categoryId").value(category.getId().toString()))
                .andExpect(jsonPath("$.suggestions[0].confidence").isNumber())
                .andExpect(jsonPath("$.suggestions[0].ruleId").isNotEmpty());
    }

    @Test
    void suggest_withMatchingStartsWithRule_shouldReturnSuggestion() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Banking");
        createRule(household, category, RuleField.IBAN, MatchType.STARTS_WITH, "BE92", 100, 90);

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "transactions": [
                        {
                            "merchant": "Unknown",
                            "communication": "Transfer",
                            "iban": "BE92123456789012",
                            "amount": 50000,
                            "date": "2026-01-01"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/classification/suggest")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions", hasSize(1)))
                .andExpect(jsonPath("$.suggestions[0].categoryId").value(category.getId().toString()));
    }

    @Test
    void suggest_withMatchingRegexRule_shouldReturnSuggestion() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Utilities");
        createRule(household, category, RuleField.COMMUNICATION, MatchType.REGEX, "FACT.*\\d+", 100, 85);

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "transactions": [
                        {
                            "merchant": "ELECTRABEL",
                            "communication": "FACTURE 12345",
                            "iban": "BE00000000000000",
                            "amount": 15000,
                            "date": "2026-01-01"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/classification/suggest")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions", hasSize(1)))
                .andExpect(jsonPath("$.suggestions[0].categoryId").value(category.getId().toString()));
    }

    @Test
    void suggest_withMultipleRules_shouldReturnHighestScoringSuggestion() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category foodCategory = createCategory(household, "Alimentation");
        Category transportCategory = createCategory(household, "Transport");
        
        // Lower priority rule
        createRule(household, transportCategory, RuleField.MERCHANT, MatchType.CONTAINS, "A", 50, 60);
        // Higher priority rule (more specific)
        createRule(household, foodCategory, RuleField.MERCHANT, MatchType.CONTAINS, "DELHAIZE", 100, 90);

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "transactions": [
                        {
                            "merchant": "DELHAIZE CITY",
                            "communication": "Grocery shopping",
                            "iban": "BE00000000000000",
                            "amount": 5000,
                            "date": "2026-01-01"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/classification/suggest")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions", hasSize(1)))
                .andExpect(jsonPath("$.suggestions[0].categoryId").value(foodCategory.getId().toString()));
    }

    @Test
    void suggest_withDisabledRule_shouldNotMatch() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Alimentation");
        
        ClassificationRule rule = ruleRepository.save(ClassificationRule.builder()
                .household(household)
                .category(category)
                .field(RuleField.MERCHANT)
                .matchType(MatchType.CONTAINS)
                .pattern("DELHAIZE")
                .enabled(false)  // Disabled
                .priority(100)
                .confidence(80)
                .source(RuleSource.USER)
                .build());

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "transactions": [
                        {
                            "merchant": "DELHAIZE CITY",
                            "communication": "Grocery shopping",
                            "iban": "BE00000000000000",
                            "amount": 5000,
                            "date": "2026-01-01"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/classification/suggest")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions", hasSize(1)))
                .andExpect(jsonPath("$.suggestions[0].categoryId").doesNotExist());
    }

    @Test
    void suggest_withMultipleTransactions_shouldReturnMultipleSuggestions() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category foodCategory = createCategory(household, "Alimentation");
        Category transportCategory = createCategory(household, "Transport");
        
        createRule(household, foodCategory, RuleField.MERCHANT, MatchType.CONTAINS, "DELHAIZE", 100, 80);
        createRule(household, transportCategory, RuleField.MERCHANT, MatchType.CONTAINS, "SNCB", 100, 80);

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "transactions": [
                        {
                            "merchant": "DELHAIZE",
                            "communication": "Shopping",
                            "iban": "BE00000000000001",
                            "amount": 5000,
                            "date": "2026-01-01"
                        },
                        {
                            "merchant": "SNCB",
                            "communication": "Train ticket",
                            "iban": "BE00000000000002",
                            "amount": 2500,
                            "date": "2026-01-02"
                        },
                        {
                            "merchant": "UNKNOWN",
                            "communication": "Unknown transaction",
                            "iban": "BE00000000000003",
                            "amount": 1000,
                            "date": "2026-01-03"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/classification/suggest")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions", hasSize(3)))
                .andExpect(jsonPath("$.suggestions[0].categoryId").value(foodCategory.getId().toString()))
                .andExpect(jsonPath("$.suggestions[1].categoryId").value(transportCategory.getId().toString()))
                .andExpect(jsonPath("$.suggestions[2].categoryId").doesNotExist());
    }

    @Test
    void suggest_withAccentedCharacters_shouldMatchNormalized() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Café");
        createRule(household, category, RuleField.MERCHANT, MatchType.CONTAINS, "CAFE", 100, 80);

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "transactions": [
                        {
                            "merchant": "Café de la Gare",
                            "communication": "Coffee",
                            "iban": "BE00000000000000",
                            "amount": 350,
                            "date": "2026-01-01"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/classification/suggest")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions", hasSize(1)))
                .andExpect(jsonPath("$.suggestions[0].categoryId").value(category.getId().toString()));
    }

    @Test
    void suggest_rulesAreNotSharedBetweenHouseholds() throws Exception {
        // Household 1 with a rule
        Household household1 = createHousehold("Household 1");
        createUser(household1, "user1@example.com", "password123");
        Category category1 = createCategory(household1, "Food");
        createRule(household1, category1, RuleField.MERCHANT, MatchType.CONTAINS, "DELHAIZE", 100, 80);

        // Household 2 without rules
        Household household2 = createHousehold("Household 2");
        createUser(household2, "user2@example.com", "password123");

        String token2 = getTokenForUser("user2@example.com", "password123");

        // User from household2 should NOT match household1's rules
        String request = """
                {
                    "transactions": [
                        {
                            "merchant": "DELHAIZE",
                            "communication": "Shopping",
                            "iban": "BE00000000000000",
                            "amount": 5000,
                            "date": "2026-01-01"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/classification/suggest")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions", hasSize(1)))
                .andExpect(jsonPath("$.suggestions[0].categoryId").doesNotExist());
    }

    @Test
    void suggest_confidenceLabelsAreCorrect() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category highCategory = createCategory(household, "High Confidence");
        Category mediumCategory = createCategory(household, "Medium Confidence");
        Category lowCategory = createCategory(household, "Low Confidence");
        
        // High confidence rule (REGEX base score 80)
        createRule(household, highCategory, RuleField.MERCHANT, MatchType.REGEX, "HIGH.*", 300, 100);
        // Medium confidence rule (CONTAINS base score 60)
        createRule(household, mediumCategory, RuleField.MERCHANT, MatchType.CONTAINS, "MEDIUM", 200, 100);
        // Low confidence rule
        createRule(household, lowCategory, RuleField.MERCHANT, MatchType.CONTAINS, "LOW", 100, 50);

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "transactions": [
                        {
                            "merchant": "HIGH TEST",
                            "communication": "",
                            "iban": "",
                            "amount": 100,
                            "date": "2026-01-01"
                        },
                        {
                            "merchant": "MEDIUM TEST",
                            "communication": "",
                            "iban": "",
                            "amount": 100,
                            "date": "2026-01-01"
                        },
                        {
                            "merchant": "LOW TEST",
                            "communication": "",
                            "iban": "",
                            "amount": 100,
                            "date": "2026-01-01"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/classification/suggest")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions", hasSize(3)))
                .andExpect(jsonPath("$.suggestions[0].confidenceLabel").value("HIGH"))
                .andExpect(jsonPath("$.suggestions[1].confidenceLabel").value("MEDIUM"))
                .andExpect(jsonPath("$.suggestions[2].confidenceLabel").value("LOW"));
    }

    @Test
    void suggest_emptyTransactionsList_shouldReturn400() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "transactions": []
                }
                """;

        mockMvc.perform(post("/api/classification/suggest")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }

    // ==================== Learn Endpoint Tests ====================

    private Account createAccount(Household household, String name) {
        return accountRepository.save(Account.builder()
                .household(household)
                .name(name)
                .type(be.gilmotech.nestspend.domain.enums.AccountType.BANK)
                .build());
    }

    private Transaction createTransaction(Household household, Account account, Category category, 
            String merchant, long amountCents) {
        return transactionRepository.save(Transaction.builder()
                .household(household)
                .account(account)
                .category(category)
                .merchant(merchant)
                .amountCents(amountCents)
                .type(be.gilmotech.nestspend.domain.enums.TransactionType.EXPENSE)
                .txDate(java.time.LocalDate.now())
                .build());
    }

    @Test
    void learn_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/classification/learn")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void learn_withNoTransactions_shouldReturnEmptyResult() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        createCategory(household, "Alimentation");

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(post("/api/classification/learn")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rulesCreated").value(0))
                .andExpect(jsonPath("$.rulesUpdated").value(0))
                .andExpect(jsonPath("$.merchantsIgnored").value(0))
                .andExpect(jsonPath("$.transactionsAnalyzed").value(0));
    }

    @Test
    void learn_withStableMerchant_shouldCreateAutoRule() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category foodCategory = createCategory(household, "Alimentation");
        Account account = createAccount(household, "Main Account");

        // Create 5 transactions with the same merchant and category (100% ratio)
        for (int i = 0; i < 5; i++) {
            createTransaction(household, account, foodCategory, "DELHAIZE CITY", 2500L + i * 100);
        }

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(post("/api/classification/learn")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rulesCreated").value(1))
                .andExpect(jsonPath("$.rulesUpdated").value(0))
                .andExpect(jsonPath("$.transactionsAnalyzed").value(5))
                .andExpect(jsonPath("$.createdRules", hasSize(1)))
                .andExpect(jsonPath("$.createdRules[0].pattern").value("DELHAIZE CITY"))
                .andExpect(jsonPath("$.createdRules[0].categoryName").value("Alimentation"))
                .andExpect(jsonPath("$.createdRules[0].confidence").value(100))
                .andExpect(jsonPath("$.createdRules[0].transactionCount").value(5));

        // Verify the rule was created
        var rules = ruleRepository.findByHouseholdId(household.getId());
        assertThat(rules, hasSize(1));
        assertThat(rules.get(0).getSource(), is(RuleSource.AUTO));
        assertThat(rules.get(0).getPattern(), is("DELHAIZE CITY"));
    }

    @Test
    void learn_withUnstableMerchant_shouldNotCreateRule() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category foodCategory = createCategory(household, "Alimentation");
        Category transportCategory = createCategory(household, "Transport");
        Account account = createAccount(household, "Main Account");

        // Create transactions where no category has 85%+ ratio
        // 3 food + 3 transport = 50% each
        for (int i = 0; i < 3; i++) {
            createTransaction(household, account, foodCategory, "GENERIC SHOP", 2500L);
            createTransaction(household, account, transportCategory, "GENERIC SHOP", 2500L);
        }

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(post("/api/classification/learn")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rulesCreated").value(0))
                .andExpect(jsonPath("$.merchantsIgnored").value(1));
    }

    @Test
    void learn_withInsufficientTransactions_shouldNotCreateRule() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category foodCategory = createCategory(household, "Alimentation");
        Account account = createAccount(household, "Main Account");

        // Create only 4 transactions (below threshold of 5)
        for (int i = 0; i < 4; i++) {
            createTransaction(household, account, foodCategory, "RARE MERCHANT", 2500L);
        }

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(post("/api/classification/learn")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rulesCreated").value(0))
                .andExpect(jsonPath("$.merchantsIgnored").value(1));
    }

    @Test
    void learn_shouldNotOverrideUserRules() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category foodCategory = createCategory(household, "Alimentation");
        Category transportCategory = createCategory(household, "Transport");
        Account account = createAccount(household, "Main Account");

        // Create a USER rule for "DELHAIZE CITY" pointing to Transport
        createRule(household, transportCategory, RuleField.MERCHANT, MatchType.CONTAINS, 
                "DELHAIZE CITY", 100, 80);

        // Create 5 transactions with DELHAIZE CITY categorized as Food
        for (int i = 0; i < 5; i++) {
            createTransaction(household, account, foodCategory, "DELHAIZE CITY", 2500L);
        }

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(post("/api/classification/learn")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rulesCreated").value(0))
                .andExpect(jsonPath("$.merchantsIgnored").value(1));

        // Verify the USER rule was not modified
        var rules = ruleRepository.findByHouseholdId(household.getId());
        assertThat(rules, hasSize(1));
        assertThat(rules.get(0).getSource(), is(RuleSource.USER));
        // Verify category by ID since we can't access lazy-loaded properties outside transaction
        assertThat(rules.get(0).getCategory().getId(), is(transportCategory.getId()));
    }

    @Test
    void learn_shouldUpdateExistingAutoRule() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category foodCategory = createCategory(household, "Alimentation");
        Category transportCategory = createCategory(household, "Transport");
        Account account = createAccount(household, "Main Account");

        // Create an existing AUTO rule pointing to Transport
        ruleRepository.save(ClassificationRule.builder()
                .household(household)
                .category(transportCategory)
                .field(RuleField.MERCHANT)
                .matchType(MatchType.CONTAINS)
                .pattern("DELHAIZE CITY")
                .enabled(true)
                .priority(50)
                .confidence(85)
                .source(RuleSource.AUTO)
                .build());

        // Create 5 transactions with DELHAIZE CITY categorized as Food
        for (int i = 0; i < 5; i++) {
            createTransaction(household, account, foodCategory, "DELHAIZE CITY", 2500L);
        }

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(post("/api/classification/learn")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rulesCreated").value(0))
                .andExpect(jsonPath("$.rulesUpdated").value(1))
                .andExpect(jsonPath("$.updatedRules", hasSize(1)))
                .andExpect(jsonPath("$.updatedRules[0].categoryName").value("Alimentation"));

        // Verify the AUTO rule was updated to Food (by category ID)
        var rules = ruleRepository.findByHouseholdId(household.getId());
        assertThat(rules, hasSize(1));
        assertThat(rules.get(0).getSource(), is(RuleSource.AUTO));
        assertThat(rules.get(0).getCategory().getId(), is(foodCategory.getId()));
    }

    @Test
    void learn_withMixedCategoriesStable_shouldCreateRule() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category foodCategory = createCategory(household, "Alimentation");
        Category transportCategory = createCategory(household, "Transport");
        Account account = createAccount(household, "Main Account");

        // Create 6 food + 1 transport = 85.7% ratio (just above threshold)
        for (int i = 0; i < 6; i++) {
            createTransaction(household, account, foodCategory, "MOSTLY FOOD", 2500L);
        }
        createTransaction(household, account, transportCategory, "MOSTLY FOOD", 2500L);

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(post("/api/classification/learn")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rulesCreated").value(1))
                .andExpect(jsonPath("$.createdRules[0].categoryName").value("Alimentation"))
                .andExpect(jsonPath("$.createdRules[0].confidence").value(86)); // 6/7 = 85.7% rounded
    }

    @Test
    void learn_multipleMerchants_shouldCreateMultipleRules() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category foodCategory = createCategory(household, "Alimentation");
        Category transportCategory = createCategory(household, "Transport");
        Account account = createAccount(household, "Main Account");

        // Create stable transactions for 2 different merchants
        for (int i = 0; i < 5; i++) {
            createTransaction(household, account, foodCategory, "COLRUYT", 2500L);
            createTransaction(household, account, transportCategory, "TOTAL ENERGIES", 5000L);
        }

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(post("/api/classification/learn")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rulesCreated").value(2))
                .andExpect(jsonPath("$.transactionsAnalyzed").value(10));

        // Verify both rules were created
        var rules = ruleRepository.findByHouseholdId(household.getId());
        assertThat(rules, hasSize(2));
    }

    @Test
    void learn_rulesNotSharedBetweenHouseholds() throws Exception {
        // Household 1 with transactions
        Household household1 = createHousehold("Household 1");
        createUser(household1, "user1@example.com", "password123");
        Category foodCategory1 = createCategory(household1, "Alimentation");
        Account account1 = createAccount(household1, "Account 1");

        for (int i = 0; i < 5; i++) {
            createTransaction(household1, account1, foodCategory1, "DELHAIZE", 2500L);
        }

        // Household 2 without transactions
        Household household2 = createHousehold("Household 2");
        createUser(household2, "user2@example.com", "password123");
        createCategory(household2, "Alimentation");

        // Learn from household 1
        String token1 = getTokenForUser("user1@example.com", "password123");
        mockMvc.perform(post("/api/classification/learn")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rulesCreated").value(1));

        // Verify household 1 has the rule
        assertThat(ruleRepository.findByHouseholdId(household1.getId()), hasSize(1));
        
        // Verify household 2 doesn't have any rules
        assertThat(ruleRepository.findByHouseholdId(household2.getId()), hasSize(0));

        // Learn from household 2 (should create no rules)
        String token2 = getTokenForUser("user2@example.com", "password123");
        mockMvc.perform(post("/api/classification/learn")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rulesCreated").value(0));
    }
}
