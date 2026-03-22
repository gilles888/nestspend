package be.gilmotech.nestspend.domain.repository;

import be.gilmotech.nestspend.domain.entity.MoisType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository JPA pour la gestion des mois types budgétaires.
 * Toutes les méthodes filtrent par household_id pour garantir l'isolation des données
 * entre les foyers (sécurité multi-tenant).
 */
@Repository
public interface MoisTypeRepository extends JpaRepository<MoisType, UUID> {

    /**
     * Recherche un mois type par son identifiant et le foyer auquel il appartient.
     * Garantit l'isolation entre les foyers.
     *
     * @param id          l'identifiant du mois type
     * @param householdId l'identifiant du foyer
     * @return le mois type s'il existe et appartient au foyer
     */
    Optional<MoisType> findByIdAndHouseholdId(UUID id, UUID householdId);

    /**
     * Récupère tous les mois types d'un foyer, triés par année décroissante puis par nom.
     *
     * @param householdId l'identifiant du foyer
     * @return liste des mois types du foyer
     */
    @Query("SELECT mt FROM MoisType mt WHERE mt.household.id = :householdId " +
           "ORDER BY mt.annee DESC, mt.nom ASC")
    List<MoisType> findByHouseholdId(@Param("householdId") UUID householdId);

    /**
     * Récupère tous les mois types d'un foyer pour une année donnée.
     *
     * @param householdId l'identifiant du foyer
     * @param annee       l'année de référence
     * @return liste des mois types pour l'année donnée
     */
    @Query("SELECT mt FROM MoisType mt WHERE mt.household.id = :householdId " +
           "AND mt.annee = :annee ORDER BY mt.nom ASC")
    List<MoisType> findByHouseholdIdAndAnnee(
            @Param("householdId") UUID householdId,
            @Param("annee") Integer annee
    );

    /**
     * Récupère un mois type avec ses dépenses types chargées (évite le problème N+1).
     *
     * @param id          l'identifiant du mois type
     * @param householdId l'identifiant du foyer
     * @return le mois type avec ses dépenses
     */
    @Query("SELECT mt FROM MoisType mt LEFT JOIN FETCH mt.depenses d " +
           "WHERE mt.id = :id AND mt.household.id = :householdId")
    Optional<MoisType> findByIdAndHouseholdIdWithDepenses(
            @Param("id") UUID id,
            @Param("householdId") UUID householdId
    );

    /**
     * Récupère le premier mois type actif pour un foyer et une année donnée.
     * Utilisé pour la projection annuelle lorsqu'un seul mois type par année est attendu.
     *
     * @param householdId l'identifiant du foyer
     * @param annee       l'année de référence
     * @return le premier mois type trouvé pour cette année, s'il existe
     */
    @Query("SELECT mt FROM MoisType mt LEFT JOIN FETCH mt.depenses d " +
           "WHERE mt.household.id = :householdId AND mt.annee = :annee " +
           "ORDER BY mt.createdAt ASC")
    List<MoisType> findByHouseholdIdAndAnneeWithDepenses(
            @Param("householdId") UUID householdId,
            @Param("annee") Integer annee
    );
}
