package be.gilmotech.nestspend.service;

import be.gilmotech.nestspend.domain.entity.Category;
import be.gilmotech.nestspend.domain.entity.Household;
import be.gilmotech.nestspend.domain.repository.CategoryRepository;
import be.gilmotech.nestspend.domain.repository.HouseholdRepository;
import be.gilmotech.nestspend.domain.repository.TransactionRepository;
import be.gilmotech.nestspend.dto.category.CategoryCreateRequest;
import be.gilmotech.nestspend.dto.category.CategoryResponse;
import be.gilmotech.nestspend.dto.category.CategoryUpdateRequest;
import be.gilmotech.nestspend.exception.ResourceConflictException;
import be.gilmotech.nestspend.exception.ResourceNotFoundException;
import be.gilmotech.nestspend.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for category-related operations with household scoping.
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final HouseholdRepository householdRepository;
    private final TransactionRepository transactionRepository;
    private final CurrentUserService currentUserService;

    /**
     * Default categories to be created for new households.
     */
    private static final List<DefaultCategory> DEFAULT_CATEGORIES = List.of(
            new DefaultCategory("Alimentation", "#4CAF50", "restaurant"),
            new DefaultCategory("Transport", "#2196F3", "directions_car"),
            new DefaultCategory("Logement", "#FF9800", "home"),
            new DefaultCategory("Santé", "#E91E63", "local_hospital"),
            new DefaultCategory("Loisirs", "#9C27B0", "sports_esports"),
            new DefaultCategory("Shopping", "#00BCD4", "shopping_bag"),
            new DefaultCategory("Factures", "#607D8B", "receipt"),
            new DefaultCategory("Éducation", "#3F51B5", "school"),
            new DefaultCategory("Épargne", "#8BC34A", "savings"),
            new DefaultCategory("Autres", "#9E9E9E", "category")
    );

    private record DefaultCategory(String name, String color, String icon) {}

    public CategoryService(CategoryRepository categoryRepository,
                           HouseholdRepository householdRepository,
                           TransactionRepository transactionRepository,
                           CurrentUserService currentUserService) {
        this.categoryRepository = categoryRepository;
        this.householdRepository = householdRepository;
        this.transactionRepository = transactionRepository;
        this.currentUserService = currentUserService;
    }

    /**
     * Create default categories for a new household.
     *
     * @param household the household to create categories for
     */
    @Transactional
    public void createDefaultCategories(Household household) {
        for (DefaultCategory defaultCategory : DEFAULT_CATEGORIES) {
            Category category = Category.builder()
                    .household(household)
                    .name(defaultCategory.name())
                    .color(defaultCategory.color())
                    .icon(defaultCategory.icon())
                    .build();
            categoryRepository.save(category);
        }
    }

    /**
     * Get all categories for the current user's household.
     *
     * @return list of category responses
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        return categoryRepository.findByHouseholdId(householdId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Get a category by ID for the current user's household.
     *
     * @param id the category ID
     * @return category response
     * @throws ResourceNotFoundException if category not found or belongs to different household
     */
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(UUID id) {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        Category category = categoryRepository.findByIdAndHouseholdId(id, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        return toResponse(category);
    }

    /**
     * Create a new category for the current user's household.
     *
     * @param request the create request
     * @return created category response
     * @throws ResourceConflictException if category name already exists in household
     */
    @Transactional
    public CategoryResponse createCategory(CategoryCreateRequest request) {
        UUID householdId = currentUserService.getCurrentHouseholdId();

        // Check for unique name within household
        if (categoryRepository.existsByHouseholdIdAndName(householdId, request.name())) {
            throw new ResourceConflictException("Category with this name already exists in household");
        }

        Category category = Category.builder()
                .household(householdRepository.getReferenceById(householdId))
                .name(request.name())
                .color(request.color())
                .icon(request.icon())
                .build();

        category = categoryRepository.save(category);
        return toResponse(category);
    }

    /**
     * Update an existing category for the current user's household.
     *
     * @param id      the category ID
     * @param request the update request
     * @return updated category response
     * @throws ResourceNotFoundException if category not found or belongs to different household
     * @throws ResourceConflictException if category name already exists in household
     */
    @Transactional
    public CategoryResponse updateCategory(UUID id, CategoryUpdateRequest request) {
        UUID householdId = currentUserService.getCurrentHouseholdId();

        Category category = categoryRepository.findByIdAndHouseholdId(id, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        // Check for unique name within household (excluding current category)
        if (categoryRepository.existsByHouseholdIdAndNameAndIdNot(householdId, request.name(), id)) {
            throw new ResourceConflictException("Category with this name already exists in household");
        }

        category.setName(request.name());
        category.setColor(request.color());
        category.setIcon(request.icon());

        category = categoryRepository.save(category);
        return toResponse(category);
    }

    /**
     * Supprime une catégorie par son identifiant.
     * La suppression est refusée si des transactions ou des budgets référencent cette catégorie.
     *
     * @param id l'identifiant de la catégorie
     * @throws ResourceNotFoundException si la catégorie n'existe pas ou appartient à un autre foyer
     * @throws ResourceConflictException si la catégorie est utilisée par des transactions ou budgets
     */
    @Transactional
    public void deleteCategory(UUID id) {
        UUID householdId = currentUserService.getCurrentHouseholdId();

        Category category = categoryRepository.findByIdAndHouseholdId(id, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        // Vérifie si des transactions référencent cette catégorie avant la suppression
        long transactionCount = transactionRepository.countByCategoryId(id);
        if (transactionCount > 0) {
            throw new ResourceConflictException(
                    "Impossible de supprimer la catégorie '" + category.getName() + "' : "
                    + transactionCount + " transaction(s) y sont rattachées. "
                    + "Reassignez les transactions à une autre catégorie avant de supprimer celle-ci.");
        }

        categoryRepository.delete(category);
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getColor(),
                category.getIcon(),
                category.getCreatedAt()
        );
    }
}
