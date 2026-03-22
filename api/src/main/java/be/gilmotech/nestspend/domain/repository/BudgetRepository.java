package be.gilmotech.nestspend.domain.repository;

import be.gilmotech.nestspend.domain.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository JPA pour la gestion des budgets mensuels par catégorie.
 */
@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    /**
     * Recherche un budget par son identifiant et le foyer auquel il appartient.
     * Garantit l'isolation entre les foyers (sécurité).
     *
     * @param id          l'identifiant du budget
     * @param householdId l'identifiant du foyer
     * @return le budget s'il existe
     */
    Optional<Budget> findByIdAndHouseholdId(UUID id, UUID householdId);

    /**
     * Récupère tous les budgets d'un foyer pour un mois donné.
     * Triés par nom de catégorie pour un affichage cohérent.
     *
     * @param householdId l'identifiant du foyer
     * @param month       le mois au format YYYY-MM
     * @return liste des budgets du mois
     */
    @Query("SELECT b FROM Budget b JOIN FETCH b.category " +
           "WHERE b.household.id = :householdId AND b.month = :month " +
           "ORDER BY b.category.name ASC")
    List<Budget> findByHouseholdIdAndMonth(
            @Param("householdId") UUID householdId,
            @Param("month") String month
    );

    /**
     * Récupère tous les budgets d'un foyer pour une année donnée (tous les mois).
     * Triés par mois puis par catégorie.
     *
     * @param householdId l'identifiant du foyer
     * @param yearPrefix  le préfixe de l'année (ex : "2025")
     * @return liste des budgets de l'année
     */
    @Query("SELECT b FROM Budget b JOIN FETCH b.category " +
           "WHERE b.household.id = :householdId AND b.month LIKE :yearPrefix% " +
           "ORDER BY b.month ASC, b.category.name ASC")
    List<Budget> findByHouseholdIdAndYear(
            @Param("householdId") UUID householdId,
            @Param("yearPrefix") String yearPrefix
    );

    /**
     * Vérifie si un budget existe déjà pour une catégorie et un mois donnés (contrainte d'unicité).
     *
     * @param householdId l'identifiant du foyer
     * @param categoryId  l'identifiant de la catégorie
     * @param month       le mois au format YYYY-MM
     * @return true si un budget existe déjà
     */
    boolean existsByHouseholdIdAndCategoryIdAndMonth(UUID householdId, UUID categoryId, String month);

    /**
     * Vérifie si un budget existe déjà pour une catégorie et un mois donnés,
     * en excluant un budget spécifique (pour la mise à jour).
     *
     * @param householdId l'identifiant du foyer
     * @param categoryId  l'identifiant de la catégorie
     * @param month       le mois au format YYYY-MM
     * @param id          l'identifiant à exclure
     * @return true si un autre budget existe déjà
     */
    boolean existsByHouseholdIdAndCategoryIdAndMonthAndIdNot(
            UUID householdId, UUID categoryId, String month, UUID id
    );

    /**
     * Récupère un budget par foyer, catégorie et mois.
     *
     * @param householdId l'identifiant du foyer
     * @param categoryId  l'identifiant de la catégorie
     * @param month       le mois au format YYYY-MM
     * @return le budget s'il existe
     */
    Optional<Budget> findByHouseholdIdAndCategoryIdAndMonth(
            UUID householdId, UUID categoryId, String month
    );
}
