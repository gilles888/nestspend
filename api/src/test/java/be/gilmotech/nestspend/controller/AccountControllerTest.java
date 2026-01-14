package be.gilmotech.nestspend.controller;

import be.gilmotech.nestspend.domain.entity.Account;
import be.gilmotech.nestspend.domain.entity.Household;
import be.gilmotech.nestspend.domain.entity.User;
import be.gilmotech.nestspend.domain.enums.AccountType;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ClassificationRuleRepository classificationRuleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        classificationRuleRepository.deleteAll();
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

    private Account createAccount(Household household, String name, AccountType type) {
        return accountRepository.save(Account.builder()
                .household(household)
                .name(name)
                .type(type)
                .build());
    }

    @Test
    void getAllAccounts_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAllAccounts_withValidToken_shouldReturnAccounts() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        createAccount(household, "Cash", AccountType.CASH);
        createAccount(household, "Checking", AccountType.BANK);

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(get("/api/accounts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Cash", "Checking")));
    }

    @Test
    void getAllAccounts_shouldOnlyReturnAccountsFromOwnHousehold() throws Exception {
        Household household1 = createHousehold("Household 1");
        createUser(household1, "user1@example.com", "password123");
        createAccount(household1, "Cash", AccountType.CASH);

        Household household2 = createHousehold("Household 2");
        createUser(household2, "user2@example.com", "password123");
        createAccount(household2, "Savings", AccountType.BANK);
        createAccount(household2, "Credit Card", AccountType.CARD);

        String token1 = getTokenForUser("user1@example.com", "password123");

        mockMvc.perform(get("/api/accounts")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Cash"));

        String token2 = getTokenForUser("user2@example.com", "password123");

        mockMvc.perform(get("/api/accounts")
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Savings", "Credit Card")));
    }

    @Test
    void getAccountById_shouldReturnAccount() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Account account = createAccount(household, "Cash", AccountType.CASH);

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(get("/api/accounts/" + account.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.getId().toString()))
                .andExpect(jsonPath("$.name").value("Cash"))
                .andExpect(jsonPath("$.type").value("CASH"));
    }

    @Test
    void getAccountById_fromDifferentHousehold_shouldReturn404() throws Exception {
        Household household1 = createHousehold("Household 1");
        createUser(household1, "user1@example.com", "password123");

        Household household2 = createHousehold("Household 2");
        createUser(household2, "user2@example.com", "password123");
        Account accountFromHousehold2 = createAccount(household2, "Cash", AccountType.CASH);

        String token1 = getTokenForUser("user1@example.com", "password123");

        mockMvc.perform(get("/api/accounts/" + accountFromHousehold2.getId())
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found"));
    }

    @Test
    void createAccount_shouldCreateAndReturnAccount() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "name": "Cash",
                    "type": "CASH"
                }
                """;

        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Cash"))
                .andExpect(jsonPath("$.type").value("CASH"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void createAccount_withDuplicateName_shouldReturn409() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        createAccount(household, "Cash", AccountType.CASH);

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "name": "Cash",
                    "type": "BANK"
                }
                """;

        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Account with this name already exists in household"));
    }

    @Test
    void createAccount_withSameNameInDifferentHousehold_shouldSucceed() throws Exception {
        Household household1 = createHousehold("Household 1");
        createUser(household1, "user1@example.com", "password123");
        createAccount(household1, "Cash", AccountType.CASH);

        Household household2 = createHousehold("Household 2");
        createUser(household2, "user2@example.com", "password123");

        String token2 = getTokenForUser("user2@example.com", "password123");

        String request = """
                {
                    "name": "Cash",
                    "type": "CASH"
                }
                """;

        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Cash"));
    }

    @Test
    void createAccount_withBlankName_shouldReturn400() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "name": "",
                    "type": "CASH"
                }
                """;

        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void createAccount_withMissingType_shouldReturn400() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "name": "Cash"
                }
                """;

        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.type").exists());
    }

    @Test
    void createAccount_withAllAccountTypes_shouldSucceed() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");

        String token = getTokenForUser("user@example.com", "password123");

        // Test CASH type
        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Cash Wallet",
                                    "type": "CASH"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CASH"));

        // Test BANK type
        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Checking Account",
                                    "type": "BANK"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("BANK"));

        // Test CARD type
        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Credit Card",
                                    "type": "CARD"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CARD"));
    }

    @Test
    void updateAccount_shouldUpdateAndReturnAccount() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Account account = createAccount(household, "Cash", AccountType.CASH);

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "name": "Petty Cash",
                    "type": "BANK"
                }
                """;

        mockMvc.perform(put("/api/accounts/" + account.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.getId().toString()))
                .andExpect(jsonPath("$.name").value("Petty Cash"))
                .andExpect(jsonPath("$.type").value("BANK"));
    }

    @Test
    void updateAccount_fromDifferentHousehold_shouldReturn404() throws Exception {
        Household household1 = createHousehold("Household 1");
        createUser(household1, "user1@example.com", "password123");

        Household household2 = createHousehold("Household 2");
        createUser(household2, "user2@example.com", "password123");
        Account accountFromHousehold2 = createAccount(household2, "Cash", AccountType.CASH);

        String token1 = getTokenForUser("user1@example.com", "password123");

        String request = """
                {
                    "name": "Updated",
                    "type": "BANK"
                }
                """;

        mockMvc.perform(put("/api/accounts/" + accountFromHousehold2.getId())
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found"));
    }

    @Test
    void updateAccount_withDuplicateName_shouldReturn409() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        createAccount(household, "Cash", AccountType.CASH);
        Account accountToUpdate = createAccount(household, "Checking", AccountType.BANK);

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "name": "Cash",
                    "type": "BANK"
                }
                """;

        mockMvc.perform(put("/api/accounts/" + accountToUpdate.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Account with this name already exists in household"));
    }

    @Test
    void updateAccount_withSameName_shouldSucceed() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Account account = createAccount(household, "Cash", AccountType.CASH);

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "name": "Cash",
                    "type": "BANK"
                }
                """;

        mockMvc.perform(put("/api/accounts/" + account.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cash"))
                .andExpect(jsonPath("$.type").value("BANK"));
    }

    @Test
    void deleteAccount_shouldDeleteAccount() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Account account = createAccount(household, "Cash", AccountType.CASH);

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(delete("/api/accounts/" + account.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Verify account is deleted
        mockMvc.perform(get("/api/accounts/" + account.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteAccount_fromDifferentHousehold_shouldReturn404() throws Exception {
        Household household1 = createHousehold("Household 1");
        createUser(household1, "user1@example.com", "password123");

        Household household2 = createHousehold("Household 2");
        createUser(household2, "user2@example.com", "password123");
        Account accountFromHousehold2 = createAccount(household2, "Cash", AccountType.CASH);

        String token1 = getTokenForUser("user1@example.com", "password123");

        mockMvc.perform(delete("/api/accounts/" + accountFromHousehold2.getId())
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found"));
    }

    @Test
    void fullCrudFlow_shouldWorkCorrectly() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");

        String token = getTokenForUser("user@example.com", "password123");

        // Create
        String createRequest = """
                {
                    "name": "Cash",
                    "type": "CASH"
                }
                """;

        String createResponse = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String accountId = objectMapper.readTree(createResponse).get("id").asText();

        // Read (list)
        mockMvc.perform(get("/api/accounts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Cash"));

        // Read (single)
        mockMvc.perform(get("/api/accounts/" + accountId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cash"));

        // Update
        String updateRequest = """
                {
                    "name": "Petty Cash",
                    "type": "BANK"
                }
                """;

        mockMvc.perform(put("/api/accounts/" + accountId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Petty Cash"));

        // Delete
        mockMvc.perform(delete("/api/accounts/" + accountId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Verify deleted
        mockMvc.perform(get("/api/accounts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
