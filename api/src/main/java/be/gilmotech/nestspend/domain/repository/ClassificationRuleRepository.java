package be.gilmotech.nestspend.domain.repository;

import be.gilmotech.nestspend.domain.entity.ClassificationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClassificationRuleRepository extends JpaRepository<ClassificationRule, UUID> {

    /**
     * Find all classification rules belonging to a specific household.
     *
     * @param householdId the household ID
     * @return list of classification rules
     */
    List<ClassificationRule> findByHouseholdId(UUID householdId);

    /**
     * Find all enabled classification rules for a household, ordered by priority descending.
     *
     * @param householdId the household ID
     * @return list of enabled classification rules sorted by priority
     */
    List<ClassificationRule> findByHouseholdIdAndEnabledTrueOrderByPriorityDesc(UUID householdId);

    /**
     * Find a classification rule by ID and household ID.
     *
     * @param id          the rule ID
     * @param householdId the household ID
     * @return optional classification rule
     */
    Optional<ClassificationRule> findByIdAndHouseholdId(UUID id, UUID householdId);

    /**
     * Check if a rule with the same pattern and field exists in the household.
     *
     * @param householdId the household ID
     * @param pattern     the pattern
     * @param field       the field
     * @return true if exists
     */
    boolean existsByHouseholdIdAndPatternIgnoreCaseAndField(
            UUID householdId,
            String pattern,
            be.gilmotech.nestspend.domain.enums.RuleField field
    );
}
