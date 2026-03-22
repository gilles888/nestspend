package be.gilmotech.nestspend.service;

import be.gilmotech.nestspend.domain.entity.Budget;
import be.gilmotech.nestspend.domain.entity.Category;
import be.gilmotech.nestspend.domain.enums.TransactionType;
import be.gilmotech.nestspend.domain.repository.BudgetRepository;
import be.gilmotech.nestspend.domain.repository.CategoryRepository;
import be.gilmotech.nestspend.domain.repository.HouseholdRepository;
import be.gilmotech.nestspend.domain.repository.TransactionRepository;
import be.gilmotech.nestspend.dto.budget.BudgetCreateRequest;
import be.gilmotech.nestspend.dto.budget.BudgetResponse;
import be.gilmotech.nestspend.dto.budget.BudgetSummaryResponse;
import be.gilmotech.nestspend.dto.budget.BudgetUpdateRequest;
import be.gilmotech.nestspend.exception.ResourceConflictException;
import be.gilmotech.nestspend.exception.ResourceNotFoundException;
import be.gilmotech.nestspend.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * Service de gestion des budgets mensuels par catégorie.
 * Permet de définir des plafonds de dépenses et de suivre leur consommation
 * en comparant avec les transactions réelles.
 */
@Service
public class BudgetService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final HouseholdRepository householdRepository;
    private final TransactionRepository transactionRepository;
    private final CurrentUserService currentUserService;

    public BudgetService(BudgetRepository budgetRepository,
                         CategoryRepository categoryRepository,
                         HouseholdRepository householdRepository,
                         TransactionRepository transactionRepository,
                         CurrentUserService currentUserService) {
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
        this.householdRepository = householdRepository;
        this.transactionRepository = transactionRepository;
        this.currentUserService = currentUserService;
    }

    /**
     * Récupère le résumé des budgets pour un mois donné, incluant les dépenses réelles.
     *
     * @param month le mois au format YYYY-MM
     * @return le résumé avec totaux agrégés et détail par catégorie
     * @throws IllegalArgumentException si le format du mois est invalide
     */
    @Transactional(readOnly = true)
    public BudgetSummaryResponse getBudgetSummary(String month) {
        validerFormatMois(month);
        UUID householdId = currentUserService.getCurrentHouseholdId();

        List<Budget> budgets = budgetRepository.findByHouseholdIdAndMonth(householdId, month);

        // Calcul des dépenses réelles pour le mois
        YearMonth yearMonth = YearMonth.parse(month, MONTH_FORMATTER);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        List<BudgetResponse> budgetResponses = budgets.stream()
                .map(b -> toBudgetResponseWithSpent(b, householdId, startDate, endDate))
                .toList();

        // Calcul des totaux
        long totalBudgeted = budgetResponses.stream().mapToLong(BudgetResponse::amountCents).sum();
        long totalSpent = budgetResponses.stream().mapToLong(BudgetResponse::spentCents).sum();
        long totalRemaining = totalBudgeted - totalSpent;
        double overallPercent = totalBudgeted > 0
                ? Math.round((double) totalSpent / totalBudgeted * 10000.0) / 100.0
                : 0.0;

        return new BudgetSummaryResponse(
                month,
                totalBudgeted,
                totalSpent,
                totalRemaining,
                overallPercent,
                budgetResponses
        );
    }

    /**
     * Récupère un budget par son identifiant.
     *
     * @param id l'identifiant du budget
     * @return le budget avec les dépenses réelles
     * @throws ResourceNotFoundException si le budget n'existe pas ou appartient à un autre foyer
     */
    @Transactional(readOnly = true)
    public BudgetResponse getBudgetById(UUID id) {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        Budget budget = budgetRepository.findByIdAndHouseholdId(id, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget non trouvé : " + id));

        YearMonth yearMonth = YearMonth.parse(budget.getMonth(), MONTH_FORMATTER);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        return toBudgetResponseWithSpent(budget, householdId, startDate, endDate);
    }

    /**
     * Crée un nouveau budget mensuel pour une catégorie.
     *
     * @param request les données du budget à créer
     * @return le budget créé
     * @throws ResourceNotFoundException si la catégorie n'existe pas
     * @throws ResourceConflictException si un budget existe déjà pour cette catégorie et ce mois
     */
    @Transactional
    public BudgetResponse createBudget(BudgetCreateRequest request) {
        UUID householdId = currentUserService.getCurrentHouseholdId();

        // Vérifie que la catégorie appartient au foyer
        Category category = categoryRepository.findByIdAndHouseholdId(request.categoryId(), householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie non trouvée : " + request.categoryId()));

        // Vérifie l'unicité du budget (catégorie + mois)
        if (budgetRepository.existsByHouseholdIdAndCategoryIdAndMonth(
                householdId, request.categoryId(), request.month())) {
            throw new ResourceConflictException(
                    "Un budget existe déjà pour la catégorie '" + category.getName()
                    + "' au mois " + request.month());
        }

        Budget budget = Budget.builder()
                .household(householdRepository.getReferenceById(householdId))
                .category(category)
                .month(request.month())
                .amountCents(request.amountCents())
                .build();

        budget = budgetRepository.save(budget);

        // Calcul des dépenses réelles pour la réponse
        YearMonth yearMonth = YearMonth.parse(request.month(), MONTH_FORMATTER);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        return toBudgetResponseWithSpent(budget, householdId, startDate, endDate);
    }

    /**
     * Met à jour le montant d'un budget existant.
     * La catégorie et le mois ne sont pas modifiables.
     *
     * @param id      l'identifiant du budget
     * @param request la requête avec le nouveau montant
     * @return le budget mis à jour
     * @throws ResourceNotFoundException si le budget n'existe pas ou appartient à un autre foyer
     */
    @Transactional
    public BudgetResponse updateBudget(UUID id, BudgetUpdateRequest request) {
        UUID householdId = currentUserService.getCurrentHouseholdId();

        Budget budget = budgetRepository.findByIdAndHouseholdId(id, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget non trouvé : " + id));

        budget.setAmountCents(request.amountCents());
        budget = budgetRepository.save(budget);

        YearMonth yearMonth = YearMonth.parse(budget.getMonth(), MONTH_FORMATTER);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        return toBudgetResponseWithSpent(budget, householdId, startDate, endDate);
    }

    /**
     * Supprime un budget par son identifiant.
     *
     * @param id l'identifiant du budget à supprimer
     * @throws ResourceNotFoundException si le budget n'existe pas ou appartient à un autre foyer
     */
    @Transactional
    public void deleteBudget(UUID id) {
        UUID householdId = currentUserService.getCurrentHouseholdId();

        Budget budget = budgetRepository.findByIdAndHouseholdId(id, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget non trouvé : " + id));

        budgetRepository.delete(budget);
    }

    /**
     * Convertit un budget en DTO de réponse en récupérant les dépenses réelles
     * pour la catégorie et le mois concernés.
     */
    private BudgetResponse toBudgetResponseWithSpent(Budget budget, UUID householdId,
                                                      LocalDate startDate, LocalDate endDate) {
        // Récupère les dépenses réelles pour cette catégorie ce mois
        Long rawSpent = transactionRepository.sumAmountByHouseholdAndCategoryAndTypeAndDateRange(
                householdId, budget.getCategory().getId(),
                TransactionType.EXPENSE, startDate, endDate);

        long spentCents = rawSpent != null ? rawSpent : 0L;
        long remainingCents = budget.getAmountCents() - spentCents;
        double consumptionPercent = budget.getAmountCents() > 0
                ? Math.round((double) spentCents / budget.getAmountCents() * 10000.0) / 100.0
                : 0.0;

        return new BudgetResponse(
                budget.getId(),
                budget.getCategory().getId(),
                budget.getCategory().getName(),
                budget.getCategory().getColor(),
                budget.getMonth(),
                budget.getAmountCents(),
                spentCents,
                remainingCents,
                consumptionPercent,
                budget.getCreatedAt(),
                budget.getUpdatedAt()
        );
    }

    /**
     * Valide que le format du mois est correct (YYYY-MM).
     *
     * @param month le mois à valider
     * @throws IllegalArgumentException si le format est invalide
     */
    private void validerFormatMois(String month) {
        if (month == null || month.isBlank()) {
            throw new IllegalArgumentException("Le mois est obligatoire");
        }
        try {
            YearMonth.parse(month, MONTH_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Format de mois invalide. Attendu : YYYY-MM, reçu : " + month);
        }
    }
}
