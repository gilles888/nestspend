package be.gilmotech.nestspend.controller;

import be.gilmotech.nestspend.domain.entity.Category;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CategoryControllerTest {

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

    @Test
    void getAllCategories_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAllCategories_withValidToken_shouldReturnCategories() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        createCategory(household, "Food");
        createCategory(household, "Transport");

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Food", "Transport")));
    }

    @Test
    void getAllCategories_shouldOnlyReturnCategoriesFromOwnHousehold() throws Exception {
        Household household1 = createHousehold("Household 1");
        createUser(household1, "user1@example.com", "password123");
        createCategory(household1, "Food");

        Household household2 = createHousehold("Household 2");
        createUser(household2, "user2@example.com", "password123");
        createCategory(household2, "Entertainment");
        createCategory(household2, "Utilities");

        String token1 = getTokenForUser("user1@example.com", "password123");

        mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Food"));

        String token2 = getTokenForUser("user2@example.com", "password123");

        mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Entertainment", "Utilities")));
    }

    @Test
    void getCategoryById_shouldReturnCategory() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Food");

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(get("/api/categories/" + category.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(category.getId().toString()))
                .andExpect(jsonPath("$.name").value("Food"))
                .andExpect(jsonPath("$.color").value("#FF0000"))
                .andExpect(jsonPath("$.icon").value("shopping"));
    }

    @Test
    void getCategoryById_fromDifferentHousehold_shouldReturn404() throws Exception {
        Household household1 = createHousehold("Household 1");
        createUser(household1, "user1@example.com", "password123");

        Household household2 = createHousehold("Household 2");
        createUser(household2, "user2@example.com", "password123");
        Category categoryFromHousehold2 = createCategory(household2, "Food");

        String token1 = getTokenForUser("user1@example.com", "password123");

        mockMvc.perform(get("/api/categories/" + categoryFromHousehold2.getId())
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Category not found"));
    }

    @Test
    void createCategory_shouldCreateAndReturnCategory() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "name": "Food",
                    "color": "#00FF00",
                    "icon": "restaurant"
                }
                """;

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Food"))
                .andExpect(jsonPath("$.color").value("#00FF00"))
                .andExpect(jsonPath("$.icon").value("restaurant"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void createCategory_withDuplicateName_shouldReturn409() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        createCategory(household, "Food");

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "name": "Food",
                    "color": "#00FF00",
                    "icon": "restaurant"
                }
                """;

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Category with this name already exists in household"));
    }

    @Test
    void createCategory_withSameNameInDifferentHousehold_shouldSucceed() throws Exception {
        Household household1 = createHousehold("Household 1");
        createUser(household1, "user1@example.com", "password123");
        createCategory(household1, "Food");

        Household household2 = createHousehold("Household 2");
        createUser(household2, "user2@example.com", "password123");

        String token2 = getTokenForUser("user2@example.com", "password123");

        String request = """
                {
                    "name": "Food",
                    "color": "#00FF00",
                    "icon": "restaurant"
                }
                """;

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Food"));
    }

    @Test
    void createCategory_withBlankName_shouldReturn400() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "name": "",
                    "color": "#00FF00",
                    "icon": "restaurant"
                }
                """;

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void updateCategory_shouldUpdateAndReturnCategory() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Food");

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "name": "Groceries",
                    "color": "#0000FF",
                    "icon": "cart"
                }
                """;

        mockMvc.perform(put("/api/categories/" + category.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(category.getId().toString()))
                .andExpect(jsonPath("$.name").value("Groceries"))
                .andExpect(jsonPath("$.color").value("#0000FF"))
                .andExpect(jsonPath("$.icon").value("cart"));
    }

    @Test
    void updateCategory_fromDifferentHousehold_shouldReturn404() throws Exception {
        Household household1 = createHousehold("Household 1");
        createUser(household1, "user1@example.com", "password123");

        Household household2 = createHousehold("Household 2");
        createUser(household2, "user2@example.com", "password123");
        Category categoryFromHousehold2 = createCategory(household2, "Food");

        String token1 = getTokenForUser("user1@example.com", "password123");

        String request = """
                {
                    "name": "Groceries",
                    "color": "#0000FF",
                    "icon": "cart"
                }
                """;

        mockMvc.perform(put("/api/categories/" + categoryFromHousehold2.getId())
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Category not found"));
    }

    @Test
    void updateCategory_withDuplicateName_shouldReturn409() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        createCategory(household, "Food");
        Category categoryToUpdate = createCategory(household, "Transport");

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "name": "Food",
                    "color": "#0000FF",
                    "icon": "cart"
                }
                """;

        mockMvc.perform(put("/api/categories/" + categoryToUpdate.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Category with this name already exists in household"));
    }

    @Test
    void updateCategory_withSameName_shouldSucceed() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Food");

        String token = getTokenForUser("user@example.com", "password123");

        String request = """
                {
                    "name": "Food",
                    "color": "#0000FF",
                    "icon": "cart"
                }
                """;

        mockMvc.perform(put("/api/categories/" + category.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Food"))
                .andExpect(jsonPath("$.color").value("#0000FF"));
    }

    @Test
    void deleteCategory_shouldDeleteCategory() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");
        Category category = createCategory(household, "Food");

        String token = getTokenForUser("user@example.com", "password123");

        mockMvc.perform(delete("/api/categories/" + category.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Verify category is deleted
        mockMvc.perform(get("/api/categories/" + category.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCategory_fromDifferentHousehold_shouldReturn404() throws Exception {
        Household household1 = createHousehold("Household 1");
        createUser(household1, "user1@example.com", "password123");

        Household household2 = createHousehold("Household 2");
        createUser(household2, "user2@example.com", "password123");
        Category categoryFromHousehold2 = createCategory(household2, "Food");

        String token1 = getTokenForUser("user1@example.com", "password123");

        mockMvc.perform(delete("/api/categories/" + categoryFromHousehold2.getId())
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Category not found"));
    }

    @Test
    void fullCrudFlow_shouldWorkCorrectly() throws Exception {
        Household household = createHousehold("Test Household");
        createUser(household, "user@example.com", "password123");

        String token = getTokenForUser("user@example.com", "password123");

        // Create
        String createRequest = """
                {
                    "name": "Food",
                    "color": "#00FF00",
                    "icon": "restaurant"
                }
                """;

        String createResponse = mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String categoryId = objectMapper.readTree(createResponse).get("id").asText();

        // Read (list)
        mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Food"));

        // Read (single)
        mockMvc.perform(get("/api/categories/" + categoryId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Food"));

        // Update
        String updateRequest = """
                {
                    "name": "Groceries",
                    "color": "#0000FF",
                    "icon": "cart"
                }
                """;

        mockMvc.perform(put("/api/categories/" + categoryId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Groceries"));

        // Delete
        mockMvc.perform(delete("/api/categories/" + categoryId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Verify deleted
        mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void register_shouldCreateDefaultCategories() throws Exception {
        // Register a new user (which creates a new household)
        String registerRequest = """
                {
                    "email": "newuser@example.com",
                    "password": "password123",
                    "displayName": "New User",
                    "householdName": "New Household"
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

        // Verify default categories were created
        mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder(
                        "Alimentation", "Transport", "Logement", "Santé", "Loisirs",
                        "Shopping", "Factures", "Éducation", "Épargne", "Autres"
                )));
    }
}
