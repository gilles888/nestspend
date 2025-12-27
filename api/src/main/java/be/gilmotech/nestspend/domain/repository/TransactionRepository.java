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
}
