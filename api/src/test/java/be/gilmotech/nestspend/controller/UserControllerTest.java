package be.gilmotech.nestspend.controller;

import be.gilmotech.nestspend.domain.entity.Household;
import be.gilmotech.nestspend.domain.entity.User;
import be.gilmotech.nestspend.domain.enums.UserRole;
import be.gilmotech.nestspend.domain.repository.AccountRepository;
import be.gilmotech.nestspend.domain.repository.CategoryRepository;
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

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

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
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
        householdRepository.deleteAll();
    }

    @Test
    void me_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withValidToken_shouldReturnUserInfo() throws Exception {
        // First register to get a token
        String registerRequest = """
                {
                    "email": "admin@example.com",
                    "password": "password123",
                    "displayName": "Admin User",
                    "householdName": "My Household"
                }
                """;

        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequest))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = objectMapper.readTree(response).get("token").asText();
        String userId = objectMapper.readTree(response).get("userId").asText();
        String householdId = objectMapper.readTree(response).get("householdId").asText();

        // Call /api/me endpoint
        mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.householdId").value(householdId))
                .andExpect(jsonPath("$.email").value("admin@example.com"))
                .andExpect(jsonPath("$.displayName").value("Admin User"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void me_afterLogin_shouldReturnCorrectUserWithHouseholdScoping() throws Exception {
        // Create first household with user
        Household household1 = householdRepository.save(Household.builder()
                .name("Household 1")
                .build());

        User user1 = userRepository.save(User.builder()
                .household(household1)
                .email("user1@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .displayName("User One")
                .role(UserRole.ADMIN)
                .build());

        // Create second household with user
        Household household2 = householdRepository.save(Household.builder()
                .name("Household 2")
                .build());

        User user2 = userRepository.save(User.builder()
                .household(household2)
                .email("user2@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .displayName("User Two")
                .role(UserRole.USER)
                .build());

        // Login as user1
        String loginRequest1 = """
                {
                    "email": "user1@example.com",
                    "password": "password123"
                }
                """;

        String response1 = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest1))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token1 = objectMapper.readTree(response1).get("token").asText();

        // Verify /api/me returns user1's information with correct household scoping
        mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user1.getId().toString()))
                .andExpect(jsonPath("$.householdId").value(household1.getId().toString()))
                .andExpect(jsonPath("$.email").value("user1@example.com"))
                .andExpect(jsonPath("$.displayName").value("User One"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        // Login as user2
        String loginRequest2 = """
                {
                    "email": "user2@example.com",
                    "password": "password123"
                }
                """;

        String response2 = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest2))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token2 = objectMapper.readTree(response2).get("token").asText();

        // Verify /api/me returns user2's information with correct household scoping
        mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user2.getId().toString()))
                .andExpect(jsonPath("$.householdId").value(household2.getId().toString()))
                .andExpect(jsonPath("$.email").value("user2@example.com"))
                .andExpect(jsonPath("$.displayName").value("User Two"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void me_withInvalidToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }
}
