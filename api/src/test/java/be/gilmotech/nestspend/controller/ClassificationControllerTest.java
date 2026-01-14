package be.gilmotech.nestspend.controller;

import be.gilmotech.nestspend.domain.entity.Category;
import be.gilmotech.nestspend.domain.entity.ClassificationRule;
import be.gilmotech.nestspend.domain.entity.Household;
import be.gilmotech.nestspend.domain.entity.User;
import be.gilmotech.nestspend.domain.enums.MatchType;
import be.gilmotech.nestspend.domain.enums.RuleField;
import be.gilmotech.nestspend.domain.enums.RuleSource;
import be.gilmotech.nestspend.domain.enums.UserRole;
import be.gilmotech.nestspend.domain.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
}
