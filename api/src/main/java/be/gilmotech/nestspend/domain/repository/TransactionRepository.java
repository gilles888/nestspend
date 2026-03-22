package be.gilmotech.nestspend.domain.repository;

import be.gilmotech.nestspend.domain.entity.Transaction;
import be.gilmotech.nestspend.domain.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * Find a transaction by ID and household ID.
     *
     * @param id          the transaction ID
     * @param householdId the household ID
     * @return optional transaction
     */
    Optional<Transaction> findByIdAndHouseholdId(UUID id, UUID householdId);

    /**
     * Sum of amount_cents for transactions of a given type in a household within a date range.
     *
     * @param householdId the household ID
     * @param type        the transaction type (INCOME or EXPENSE)
     * @param startDate   the start date (inclusive)
     * @param endDate     the end date (inclusive)
     * @return sum of amount_cents, or null if no transactions
     */
    @Query("SELECT COALESCE(SUM(t.amountCents), 0) FROM Transaction t " +
           "WHERE t.household.id = :householdId " +
           "AND t.type = :type " +
           "AND t.txDate >= :startDate " +
           "AND t.txDate <= :endDate")
    Long sumAmountByHouseholdAndTypeAndDateRange(
            @Param("householdId") UUID householdId,
            @Param("type") TransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Sum of expense amounts grouped by category for a household within a date range.
     *
     * @param householdId the household ID
     * @param type        the transaction type (should be EXPENSE)
     * @param startDate   the start date (inclusive)
     * @param endDate     the end date (inclusive)
     * @return list of Object arrays [categoryId, categoryName, sumAmountCents]
     */
    @Query("SELECT t.category.id, t.category.name, COALESCE(SUM(t.amountCents), 0) " +
           "FROM Transaction t " +
           "WHERE t.household.id = :householdId " +
           "AND t.type = :type " +
           "AND t.txDate >= :startDate " +
           "AND t.txDate <= :endDate " +
           "GROUP BY t.category.id, t.category.name " +
           "ORDER BY SUM(t.amountCents) DESC")
    List<Object[]> sumExpensesByCategory(
            @Param("householdId") UUID householdId,
            @Param("type") TransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Find all transactions for a household with optional filters.
     * All filters are optional - if a filter parameter is null, it is ignored.
     *
     * @param householdId the household ID (required)
     * @param fromDate    optional start date filter (inclusive)
     * @param toDate      optional end date filter (inclusive)
     * @param categoryId  optional category ID filter
     * @param accountId   optional account ID filter
     * @param type        optional transaction type filter
     * @return list of matching transactions ordered by txDate descending
     */
    @Query("SELECT t FROM Transaction t " +
           "WHERE t.household.id = :householdId " +
           "AND (:fromDate IS NULL OR t.txDate >= :fromDate) " +
           "AND (:toDate IS NULL OR t.txDate <= :toDate) " +
           "AND (:categoryId IS NULL OR t.category.id = :categoryId) " +
           "AND (:accountId IS NULL OR t.account.id = :accountId) " +
           "AND (:type IS NULL OR t.type = :type) " +
           "ORDER BY t.txDate DESC, t.createdAt DESC")
    List<Transaction> findByHouseholdIdWithFilters(
            @Param("householdId") UUID householdId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("categoryId") UUID categoryId,
            @Param("accountId") UUID accountId,
            @Param("type") TransactionType type
    );

    /**
     * Find transactions for a specific account within a date range.
     * Used for deduplication during import.
     *
     * @param accountId the account ID
     * @param householdId the household ID (for security)
     * @param fromDate start date (inclusive)
     * @param toDate end date (inclusive)
     * @return list of transactions in the specified period
     */
    @Query("SELECT t FROM Transaction t " +
           "WHERE t.account.id = :accountId " +
           "AND t.household.id = :householdId " +
           "AND t.txDate >= :fromDate " +
           "AND t.txDate <= :toDate")
    List<Transaction> findByAccountIdAndHouseholdIdAndDateRange(
            @Param("accountId") UUID accountId,
            @Param("householdId") UUID householdId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    /**
     * Compte le nombre de transactions référençant une catégorie spécifique.
     * Utilisé pour empêcher la suppression d'une catégorie encore utilisée.
     *
     * @param categoryId l'identifiant de la catégorie
     * @return le nombre de transactions liées à cette catégorie
     */
    long countByCategoryId(UUID categoryId);

    /**
     * Compte le nombre de transactions référençant un compte spécifique.
     * Utilisé pour empêcher la suppression d'un compte encore utilisé.
     *
     * @param accountId l'identifiant du compte
     * @return le nombre de transactions liées à ce compte
     */
    long countByAccountId(UUID accountId);

    /**
     * Somme des montants pour une catégorie spécifique, un type et une plage de dates.
     * Utilisée pour le calcul des dépenses réelles dans le cadre du suivi budgétaire.
     *
     * @param householdId l'identifiant du foyer
     * @param categoryId  l'identifiant de la catégorie
     * @param type        le type de transaction (EXPENSE généralement)
     * @param startDate   la date de début (inclusive)
     * @param endDate     la date de fin (inclusive)
     * @return somme des montants en centimes, ou 0 si aucune transaction
     */
    @Query("SELECT COALESCE(SUM(t.amountCents), 0) FROM Transaction t " +
           "WHERE t.household.id = :householdId " +
           "AND t.category.id = :categoryId " +
           "AND t.type = :type " +
           "AND t.txDate >= :startDate " +
           "AND t.txDate <= :endDate")
    Long sumAmountByHouseholdAndCategoryAndTypeAndDateRange(
            @Param("householdId") UUID householdId,
            @Param("categoryId") UUID categoryId,
            @Param("type") TransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Get merchant-category statistics for learning algorithm.
     * Groups transactions by normalized merchant (UPPER, trimmed) and category,
     * returns counts for analysis.
     *
     * @param householdId the household ID
     * @return list of Object arrays [normalizedMerchant, categoryId, categoryName, count]
     */
    @Query("SELECT UPPER(TRIM(t.merchant)), t.category.id, t.category.name, COUNT(t) " +
           "FROM Transaction t " +
           "WHERE t.household.id = :householdId " +
           "AND t.merchant IS NOT NULL " +
           "AND TRIM(t.merchant) <> '' " +
           "GROUP BY UPPER(TRIM(t.merchant)), t.category.id, t.category.name " +
           "ORDER BY UPPER(TRIM(t.merchant)), COUNT(t) DESC")
    List<Object[]> getMerchantCategoryStats(@Param("householdId") UUID householdId);
}
