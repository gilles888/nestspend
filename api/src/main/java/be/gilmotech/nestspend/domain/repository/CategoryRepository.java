package be.gilmotech.nestspend.domain.repository;

import be.gilmotech.nestspend.domain.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    /**
     * Find all categories belonging to a specific household.
     *
     * @param householdId the household ID
     * @return list of categories
     */
    List<Category> findByHouseholdId(UUID householdId);

    /**
     * Find a category by ID and household ID.
     *
     * @param id          the category ID
     * @param householdId the household ID
     * @return optional category
     */
    Optional<Category> findByIdAndHouseholdId(UUID id, UUID householdId);

    /**
     * Check if a category with the given name exists in the household.
     *
     * @param householdId the household ID
     * @param name        the category name
     * @return true if exists
     */
    boolean existsByHouseholdIdAndName(UUID householdId, String name);

    /**
     * Check if a category with the given name exists in the household, excluding a specific category ID.
     *
     * @param householdId the household ID
     * @param name        the category name
     * @param id          the category ID to exclude
     * @return true if exists
     */
    boolean existsByHouseholdIdAndNameAndIdNot(UUID householdId, String name, UUID id);
}
