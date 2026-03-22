package be.gilmotech.nestspend.domain.repository;

import be.gilmotech.nestspend.domain.entity.DepenseType;
import be.gilmotech.nestspend.domain.enums.CategorieDepense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository JPA pour la gestion des dépenses types.
 * Toutes les méthodes filtrent par household_id pour garantir l'isolation des données
 * entre les foyers (sécurité multi-tenant).
 */
@Repository
public interface DepenseTypeRepository extends JpaRepository<DepenseType, UUID> {

    /**
     * Recherche une dépense type par son identifiant et le foyer.
     * Garantit l'isolation entre les foyers.
     *
     * @param id          l'identifiant de la dépense type
     * @param householdId l'identifiant du foyer
     * @return la dépense type si elle existe et appartient au foyer
     */
    Optional<DepenseType> findByIdAndHouseholdId(UUID id, UUID householdId);

    /**
     * Récupère toutes les dépenses types d'un mois type donné, triées par catégorie puis nom.
     *
     * @param moisTypeId  l'identifiant du mois type
     * @param householdId l'identifiant du foyer (vérification de sécurité)
     * @return liste des dépenses types du mois type
     */
    @Query("SELECT dt FROM DepenseType dt WHERE dt.moisType.id = :moisTypeId " +
           "AND dt.household.id = :householdId " +
           "ORDER BY dt.categorie ASC, dt.nom ASC")
    List<DepenseType> findByMoisTypeIdAndHouseholdId(
            @Param("moisTypeId") UUID moisTypeId,
            @Param("householdId") UUID householdId
    );

    /**
     * Récupère uniquement les dépenses types actives d'un mois type.
     * Utilisé pour les calculs de projection (les inactives sont exclues).
     *
     * @param moisTypeId  l'identifiant du mois type
     * @param householdId l'identifiant du foyer
     * @return liste des dépenses actives
     */
    @Query("SELECT dt FROM DepenseType dt WHERE dt.moisType.id = :moisTypeId " +
           "AND dt.household.id = :householdId AND dt.actif = true " +
           "ORDER BY dt.categorie ASC, dt.nom ASC")
    List<DepenseType> findActiveByMoisTypeIdAndHouseholdId(
            @Param("moisTypeId") UUID moisTypeId,
            @Param("householdId") UUID householdId
    );

    /**
     * Récupère les dépenses types d'un foyer filtrées par catégorie.
     *
     * @param householdId l'identifiant du foyer
     * @param categorie   la catégorie recherchée
     * @return liste des dépenses types de cette catégorie
     */
    @Query("SELECT dt FROM DepenseType dt WHERE dt.household.id = :householdId " +
           "AND dt.categorie = :categorie ORDER BY dt.nom ASC")
    List<DepenseType> findByHouseholdIdAndCategorie(
            @Param("householdId") UUID householdId,
            @Param("categorie") CategorieDepense categorie
    );

    /**
     * Compte le nombre de dépenses types actives dans un mois type.
     * Utile pour valider qu'un mois type contient au moins une dépense.
     *
     * @param moisTypeId l'identifiant du mois type
     * @return le nombre de dépenses actives
     */
    long countByMoisTypeIdAndActifTrue(UUID moisTypeId);
}
