package be.gilmotech.nestspend.domain.repository;

import be.gilmotech.nestspend.domain.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    /**
     * Find all accounts belonging to a specific household.
     *
     * @param householdId the household ID
     * @return list of accounts
     */
    List<Account> findByHouseholdId(UUID householdId);

    /**
     * Find an account by ID and household ID.
     *
     * @param id          the account ID
     * @param householdId the household ID
     * @return optional account
     */
    Optional<Account> findByIdAndHouseholdId(UUID id, UUID householdId);

    /**
     * Check if an account with the given name exists in the household.
     *
     * @param householdId the household ID
     * @param name        the account name
     * @return true if exists
     */
    boolean existsByHouseholdIdAndName(UUID householdId, String name);

    /**
     * Check if an account with the given name exists in the household, excluding a specific account ID.
     *
     * @param householdId the household ID
     * @param name        the account name
     * @param id          the account ID to exclude
     * @return true if exists
     */
    boolean existsByHouseholdIdAndNameAndIdNot(UUID householdId, String name, UUID id);
}
