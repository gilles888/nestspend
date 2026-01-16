package be.gilmotech.nestspend.controller;

import be.gilmotech.nestspend.domain.entity.FutureEvent;
import be.gilmotech.nestspend.domain.entity.Household;
import be.gilmotech.nestspend.domain.entity.User;
import be.gilmotech.nestspend.domain.enums.Periodicity;
import be.gilmotech.nestspend.domain.enums.TransactionType;
import be.gilmotech.nestspend.domain.enums.UserRole;
import be.gilmotech.nestspend.domain.repository.AccountRepository;
import be.gilmotech.nestspend.domain.repository.CategoryRepository;
import be.gilmotech.nestspend.domain.repository.ClassificationRuleRepository;
import be.gilmotech.nestspend.domain.repository.FutureEventRepository;
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
class FutureEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private FutureEventRepository futureEventRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ClassificationRuleRepository classificationRuleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        futureEventRepository.deleteAll();
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

    private FutureEvent createFutureEvent(Household household, String name, Long amountCents,
                                          TransactionType type, Periodicity periodicity,
                                          LocalDate startDate, LocalDate endDate) {
        return futureEventRepository.save(FutureEvent.builder()
                .household(household)
                .name(name)
                .amountCents(amountCents)
                .type(type)
                .periodicity(periodicity)
                .startDate(startDate)
                .endDate(endDate)
                .build());
    }

    // ==================== AUTHORIZATION TESTS ====================

    @Test
    void getAllFutureEvents_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/future-events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createFutureEvent_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/future-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== GET ALL TESTS ====================

    @Test
    void getAllFutureEvents_shouldReturnEmpty() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(get("/api/future-events")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getAllFutureEvents_shouldReturnEvents() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");

        createFutureEvent(household, "Netflix", 1599L, TransactionType.EXPENSE,
                Periodicity.MONTHLY, LocalDate.now(), null);
        createFutureEvent(household, "Salary", 300000L, TransactionType.INCOME,
                Periodicity.MONTHLY, LocalDate.now(), null);

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(get("/api/future-events")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // ==================== CREATE TESTS ====================

    @Test
    void createFutureEvent_shouldSucceed() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "name": "Netflix",
                    "amountCents": 1599,
                    "type": "EXPENSE",
                    "periodicity": "MONTHLY",
                    "startDate": "2025-01-01"
                }
                """;

        mockMvc.perform(post("/api/future-events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Netflix"))
                .andExpect(jsonPath("$.amountCents").value(1599))
                .andExpect(jsonPath("$.type").value("EXPENSE"))
                .andExpect(jsonPath("$.periodicity").value("MONTHLY"));
    }

    @Test
    void createFutureEvent_withEndDate_shouldSucceed() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "name": "Gym Membership",
                    "amountCents": 4999,
                    "type": "EXPENSE",
                    "periodicity": "MONTHLY",
                    "startDate": "2025-01-01",
                    "endDate": "2025-12-31"
                }
                """;

        mockMvc.perform(post("/api/future-events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.endDate").value("2025-12-31"));
    }

    @Test
    void createFutureEvent_withInvalidName_shouldReturn400() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "name": "A",
                    "amountCents": 1599,
                    "type": "EXPENSE",
                    "periodicity": "MONTHLY",
                    "startDate": "2025-01-01"
                }
                """;

        mockMvc.perform(post("/api/future-events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }

    // ==================== UPDATE TESTS ====================

    @Test
    void updateFutureEvent_shouldSucceed() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");

        FutureEvent event = createFutureEvent(household, "Netflix", 1599L, TransactionType.EXPENSE,
                Periodicity.MONTHLY, LocalDate.of(2025, 1, 1), null);

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "name": "Netflix Premium",
                    "amountCents": 2299,
                    "type": "EXPENSE",
                    "periodicity": "MONTHLY",
                    "startDate": "2025-01-01"
                }
                """;

        mockMvc.perform(put("/api/future-events/" + event.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Netflix Premium"))
                .andExpect(jsonPath("$.amountCents").value(2299));
    }

    @Test
    void updateFutureEvent_notFound_shouldReturn404() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "name": "Netflix",
                    "amountCents": 1599,
                    "type": "EXPENSE",
                    "periodicity": "MONTHLY",
                    "startDate": "2025-01-01"
                }
                """;

        mockMvc.perform(put("/api/future-events/00000000-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isNotFound());
    }

    // ==================== DELETE TESTS ====================

    @Test
    void deleteFutureEvent_shouldSucceed() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");

        FutureEvent event = createFutureEvent(household, "Netflix", 1599L, TransactionType.EXPENSE,
                Periodicity.MONTHLY, LocalDate.of(2025, 1, 1), null);

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(delete("/api/future-events/" + event.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Verify deleted
        mockMvc.perform(get("/api/future-events")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void deleteFutureEvent_notFound_shouldReturn404() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(delete("/api/future-events/00000000-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ==================== PROJECTIONS TESTS ====================

    @Test
    void getProjections_shouldReturnData() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");

        // Create some future events
        createFutureEvent(household, "Salary", 300000L, TransactionType.INCOME,
                Periodicity.MONTHLY, LocalDate.now().minusMonths(1), null);
        createFutureEvent(household, "Rent", 80000L, TransactionType.EXPENSE,
                Periodicity.MONTHLY, LocalDate.now().minusMonths(1), null);

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(get("/api/future-events/projections")
                        .header("Authorization", "Bearer " + token)
                        .param("months", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").exists())
                .andExpect(jsonPath("$.endDate").exists())
                .andExpect(jsonPath("$.dataPoints").isArray())
                .andExpect(jsonPath("$.dataPoints", hasSize(6)));
    }

    @Test
    void getProjections_withInvalidMonths_shouldReturn400() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(get("/api/future-events/projections")
                        .header("Authorization", "Bearer " + token)
                        .param("months", "13"))
                .andExpect(status().isBadRequest());
    }

    // ==================== HOUSEHOLD SCOPING TESTS ====================

    @Test
    void getFutureEvents_shouldOnlyReturnOwnHouseholdEvents() throws Exception {
        // Household 1
        Household household1 = createHousehold("Household 1");
        createUser(household1, "user1@example.com", "password123");
        createFutureEvent(household1, "Event 1", 1000L, TransactionType.EXPENSE,
                Periodicity.MONTHLY, LocalDate.now(), null);

        // Household 2
        Household household2 = createHousehold("Household 2");
        createUser(household2, "user2@example.com", "password123");
        createFutureEvent(household2, "Event 2", 2000L, TransactionType.EXPENSE,
                Periodicity.MONTHLY, LocalDate.now(), null);

        String token1 = getTokenForUser("user1@example.com", "password123");

        // User 1 should only see their event
        mockMvc.perform(get("/api/future-events")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Event 1"));
    }
}
