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
class ClassificationRuleControllerTest {

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

    private ClassificationRule createRule(Household household, Category category, String pattern) {
        return ruleRepository.save(ClassificationRule.builder()
                .household(household)
                .category(category)
                .field(RuleField.MERCHANT)
                .matchType(MatchType.CONTAINS)
                .pattern(pattern)
                .enabled(true)
                .priority(100)
                .confidence(80)
                .source(RuleSource.USER)
                .build());
    }

    @Test
    void getAllRules_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/classification-rules"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAllRules_withValidToken_shouldReturnRules() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Alimentation");
        createRule(household, category, "DELHAIZE");
        createRule(household, category, "COLRUYT");

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(get("/api/classification-rules")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].pattern", containsInAnyOrder("DELHAIZE", "COLRUYT")));
    }

    @Test
    void getAllRules_shouldOnlyReturnRulesFromOwnHousehold() throws Exception {
        Household household1 = createHousehold("Household 1");
        createUser(household1, "user1@example.com", "password123");
        Category category1 = createCategory(household1, "Food");
        createRule(household1, category1, "DELHAIZE");

        Household household2 = createHousehold("Household 2");
        createUser(household2, "user2@example.com", "password123");
        Category category2 = createCategory(household2, "Transport");
        createRule(household2, category2, "SNCB");
        createRule(household2, category2, "DE LIJN");

        String token1 = getTokenForUser("user1@example.com", "password123");

        mockMvc.perform(get("/api/classification-rules")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].pattern").value("DELHAIZE"));
    }

    @Test
    void getRuleById_shouldReturnRule() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Alimentation");
        ClassificationRule rule = createRule(household, category, "DELHAIZE");

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(get("/api/classification-rules/" + rule.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(rule.getId().toString()))
                .andExpect(jsonPath("$.pattern").value("DELHAIZE"))
                .andExpect(jsonPath("$.field").value("MERCHANT"))
                .andExpect(jsonPath("$.matchType").value("CONTAINS"));
    }

    @Test
    void getRuleById_fromDifferentHousehold_shouldReturn404() throws Exception {
        Household household1 = createHousehold("Household 1");
        createUser(household1, "user1@example.com", "password123");

        Household household2 = createHousehold("Household 2");
        createUser(household2, "user2@example.com", "password123");
        Category category2 = createCategory(household2, "Food");
        ClassificationRule ruleFromHousehold2 = createRule(household2, category2, "DELHAIZE");

        String token1 = getTokenForUser("user1@example.com", "password123");

        mockMvc.perform(get("/api/classification-rules/" + ruleFromHousehold2.getId())
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNotFound());
    }

    @Test
    void createRule_shouldCreateAndReturnRule() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Alimentation");

        String token = getTokenForUser("user@example.com", "password123");

        String request = String.format("""
                {
                    "field": "MERCHANT",
                    "matchType": "CONTAINS",
                    "pattern": "DELHAIZE",
                    "categoryId": "%s",
                    "enabled": true,
                    "priority": 100,
                    "confidence": 85
                }
                """, category.getId());

        mockMvc.perform(post("/api/classification-rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.pattern").value("DELHAIZE"))
                .andExpect(jsonPath("$.field").value("MERCHANT"))
                .andExpect(jsonPath("$.matchType").value("CONTAINS"))
                .andExpect(jsonPath("$.categoryId").value(category.getId().toString()))
                .andExpect(jsonPath("$.categoryName").value("Alimentation"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.priority").value(100))
                .andExpect(jsonPath("$.confidence").value(85))
                .andExpect(jsonPath("$.source").value("USER"));
    }

    @Test
    void createRule_withInvalidCategoryId_shouldReturn404() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "field": "MERCHANT",
                    "matchType": "CONTAINS",
                    "pattern": "DELHAIZE",
                    "categoryId": "00000000-0000-0000-0000-000000000000"
                }
                """;

        mockMvc.perform(post("/api/classification-rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isNotFound());
    }

    @Test
    void createRule_withInvalidRegex_shouldReturn400() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Alimentation");

        String token = getTokenForUser("user@example.com", "password123");

        String request = String.format("""
                {
                    "field": "MERCHANT",
                    "matchType": "REGEX",
                    "pattern": "[invalid(regex",
                    "categoryId": "%s"
                }
                """, category.getId());

        mockMvc.perform(post("/api/classification-rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateRule_shouldUpdateAndReturnRule() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category category1 = createCategory(household, "Alimentation");
        Category category2 = createCategory(household, "Transport");
        ClassificationRule rule = createRule(household, category1, "DELHAIZE");

        String token = getTokenForUser("user@example.com", "password123");

        String request = String.format("""
                {
                    "field": "IBAN",
                    "matchType": "STARTS_WITH",
                    "pattern": "BE92",
                    "categoryId": "%s",
                    "enabled": false,
                    "priority": 50,
                    "confidence": 70
                }
                """, category2.getId());

        mockMvc.perform(put("/api/classification-rules/" + rule.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(rule.getId().toString()))
                .andExpect(jsonPath("$.pattern").value("BE92"))
                .andExpect(jsonPath("$.field").value("IBAN"))
                .andExpect(jsonPath("$.matchType").value("STARTS_WITH"))
                .andExpect(jsonPath("$.categoryId").value(category2.getId().toString()))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.priority").value(50))
                .andExpect(jsonPath("$.confidence").value(70));
    }

    @Test
    void updateRule_fromDifferentHousehold_shouldReturn404() throws Exception {
        Household household1 = createHousehold("Household 1");
        createUser(household1, "user1@example.com", "password123");
        Category category1 = createCategory(household1, "Food");

        Household household2 = createHousehold("Household 2");
        createUser(household2, "user2@example.com", "password123");
        Category category2 = createCategory(household2, "Transport");
        ClassificationRule ruleFromHousehold2 = createRule(household2, category2, "SNCB");

        String token1 = getTokenForUser("user1@example.com", "password123");

        String request = String.format("""
                {
                    "field": "MERCHANT",
                    "matchType": "CONTAINS",
                    "pattern": "NEW",
                    "categoryId": "%s",
                    "enabled": true,
                    "priority": 50,
                    "confidence": 70
                }
                """, category1.getId());

        mockMvc.perform(put("/api/classification-rules/" + ruleFromHousehold2.getId())
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRule_shouldDeleteRule() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Alimentation");
        ClassificationRule rule = createRule(household, category, "DELHAIZE");

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(delete("/api/classification-rules/" + rule.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Verify rule is deleted
        mockMvc.perform(get("/api/classification-rules/" + rule.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRule_fromDifferentHousehold_shouldReturn404() throws Exception {
        Household household1 = createHousehold("Household 1");
        createUser(household1, "user1@example.com", "password123");

        Household household2 = createHousehold("Household 2");
        createUser(household2, "user2@example.com", "password123");
        Category category2 = createCategory(household2, "Food");
        ClassificationRule ruleFromHousehold2 = createRule(household2, category2, "DELHAIZE");

        String token1 = getTokenForUser("user1@example.com", "password123");

        mockMvc.perform(delete("/api/classification-rules/" + ruleFromHousehold2.getId())
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNotFound());
    }

    @Test
    void fullCrudFlow_shouldWorkCorrectly() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Alimentation");

        String token = getTokenForUser("user@example.com", "password123");

        // Create
        String createRequest = String.format("""
                {
                    "field": "MERCHANT",
                    "matchType": "CONTAINS",
                    "pattern": "DELHAIZE",
                    "categoryId": "%s",
                    "enabled": true,
                    "priority": 100,
                    "confidence": 85
                }
                """, category.getId());

        String createResponse = mockMvc.perform(post("/api/classification-rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String ruleId = objectMapper.readTree(createResponse).get("id").asText();

        // Read (list)
        mockMvc.perform(get("/api/classification-rules")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].pattern").value("DELHAIZE"));

        // Read (single)
        mockMvc.perform(get("/api/classification-rules/" + ruleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pattern").value("DELHAIZE"));

        // Update
        String updateRequest = String.format("""
                {
                    "field": "MERCHANT",
                    "matchType": "STARTS_WITH",
                    "pattern": "COLRUYT",
                    "categoryId": "%s",
                    "enabled": false,
                    "priority": 50,
                    "confidence": 70
                }
                """, category.getId());

        mockMvc.perform(put("/api/classification-rules/" + ruleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pattern").value("COLRUYT"));

        // Delete
        mockMvc.perform(delete("/api/classification-rules/" + ruleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Verify deleted
        mockMvc.perform(get("/api/classification-rules")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
