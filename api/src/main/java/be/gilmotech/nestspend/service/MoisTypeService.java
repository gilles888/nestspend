package be.gilmotech.nestspend.service;

import be.gilmotech.nestspend.domain.entity.DepenseType;
import be.gilmotech.nestspend.domain.entity.Household;
import be.gilmotech.nestspend.domain.entity.MoisType;
import be.gilmotech.nestspend.domain.enums.FrequenceDepense;
import be.gilmotech.nestspend.domain.enums.TypeDepense;
import be.gilmotech.nestspend.domain.repository.DepenseTypeRepository;
import be.gilmotech.nestspend.domain.repository.HouseholdRepository;
import be.gilmotech.nestspend.domain.repository.MoisTypeRepository;
import be.gilmotech.nestspend.dto.moistype.DepenseTypeCreateRequest;
import be.gilmotech.nestspend.dto.moistype.DepenseTypeDto;
import be.gilmotech.nestspend.dto.moistype.DepenseTypeUpdateRequest;
import be.gilmotech.nestspend.dto.moistype.MoisTypeCreateRequest;
import be.gilmotech.nestspend.dto.moistype.MoisTypeDto;
import be.gilmotech.nestspend.dto.moistype.MoisTypeUpdateRequest;
import be.gilmotech.nestspend.exception.ResourceNotFoundException;
import be.gilmotech.nestspend.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service de gestion des mois types budgétaires.
 * Permet de définir un mois type avec ses dépenses récurrentes
 * pour calculer des projections mensuelles et annuelles d'épargne.
 */
@Service
public class MoisTypeService {

    private final MoisTypeRepository moisTypeRepository;
    private final DepenseTypeRepository depenseTypeRepository;
    private final HouseholdRepository householdRepository;
    private final CurrentUserService currentUserService;

    public MoisTypeService(MoisTypeRepository moisTypeRepository,
                           DepenseTypeRepository depenseTypeRepository,
                           HouseholdRepository householdRepository,
                           CurrentUserService currentUserService) {
        this.moisTypeRepository = moisTypeRepository;
        this.depenseTypeRepository = depenseTypeRepository;
        this.householdRepository = householdRepository;
        this.currentUserService = currentUserService;
    }

    // =========================================================================
    // Méthodes sur les mois types
    // =========================================================================

    /**
     * Récupère tous les mois types du foyer courant, sans le détail des dépenses.
     *
     * @return liste des mois types avec leurs totaux calculés
     */
    @Transactional(readOnly = true)
    public List<MoisTypeDto> listerMoisTypes() {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        List<MoisType> moisTypes = moisTypeRepository.findByHouseholdId(householdId);
        return moisTypes.stream()
                .map(mt -> toMoisTypeDto(mt, false))
                .toList();
    }

    /**
     * Récupère un mois type par son identifiant, avec le détail complet des dépenses.
     *
     * @param id l'identifiant du mois type
     * @return le mois type avec ses dépenses
     * @throws ResourceNotFoundException si le mois type n'appartient pas au foyer courant
     */
    @Transactional(readOnly = true)
    public MoisTypeDto getMoisTypeDetail(UUID id) {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        MoisType moisType = moisTypeRepository.findByIdAndHouseholdIdWithDepenses(id, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Mois type non trouvé : " + id));
        return toMoisTypeDto(moisType, true);
    }

    /**
     * Crée un nouveau mois type pour le foyer courant.
     *
     * @param request les données du mois type à créer
     * @return le mois type créé
     */
    @Transactional
    public MoisTypeDto creerMoisType(MoisTypeCreateRequest request) {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Foyer non trouvé : " + householdId));

        MoisType moisType = MoisType.builder()
                .household(household)
                .nom(request.nom())
                .annee(request.annee())
                .revenus(request.revenus())
                .autresRevenus(request.autresRevenus() != null ? request.autresRevenus() : 0L)
                .build();

        MoisType saved = moisTypeRepository.save(moisType);
        return toMoisTypeDto(saved, true);
    }

    /**
     * Met à jour les informations d'un mois type existant.
     *
     * @param id      l'identifiant du mois type
     * @param request les nouvelles données
     * @return le mois type mis à jour
     * @throws ResourceNotFoundException si le mois type n'appartient pas au foyer courant
     */
    @Transactional
    public MoisTypeDto mettreAJourMoisType(UUID id, MoisTypeUpdateRequest request) {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        MoisType moisType = moisTypeRepository.findByIdAndHouseholdId(id, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Mois type non trouvé : " + id));

        moisType.setNom(request.nom());
        moisType.setAnnee(request.annee());
        moisType.setRevenus(request.revenus());
        moisType.setAutresRevenus(request.autresRevenus() != null ? request.autresRevenus() : 0L);

        MoisType saved = moisTypeRepository.save(moisType);
        return toMoisTypeDto(saved, false);
    }

    /**
     * Supprime un mois type et toutes ses dépenses (cascade).
     *
     * @param id l'identifiant du mois type
     * @throws ResourceNotFoundException si le mois type n'appartient pas au foyer courant
     */
    @Transactional
    public void supprimerMoisType(UUID id) {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        MoisType moisType = moisTypeRepository.findByIdAndHouseholdId(id, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Mois type non trouvé : " + id));
        moisTypeRepository.delete(moisType);
    }

    // =========================================================================
    // Méthodes sur les dépenses types
    // =========================================================================

    /**
     * Crée une nouvelle dépense type et la rattache au mois type spécifié.
     *
     * @param request les données de la dépense type
     * @return la dépense type créée
     * @throws ResourceNotFoundException si le mois type n'appartient pas au foyer courant
     */
    @Transactional
    public DepenseTypeDto creerDepenseType(DepenseTypeCreateRequest request) {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Foyer non trouvé : " + householdId));

        MoisType moisType = moisTypeRepository.findByIdAndHouseholdId(request.moisTypeId(), householdId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Mois type non trouvé : " + request.moisTypeId()));

        DepenseType depenseType = DepenseType.builder()
                .household(household)
                .moisType(moisType)
                .nom(request.nom())
                .montant(request.montant())
                .categorie(request.categorie())
                .typeDepense(request.typeDepense())
                .frequence(request.frequence())
                .actif(true)
                .build();

        DepenseType saved = depenseTypeRepository.save(depenseType);
        return toDepenseTypeDto(saved);
    }

    /**
     * Met à jour une dépense type existante.
     *
     * @param id      l'identifiant de la dépense type
     * @param request les nouvelles données
     * @return la dépense type mise à jour
     * @throws ResourceNotFoundException si la dépense type n'appartient pas au foyer courant
     */
    @Transactional
    public DepenseTypeDto mettreAJourDepenseType(UUID id, DepenseTypeUpdateRequest request) {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        DepenseType depenseType = depenseTypeRepository.findByIdAndHouseholdId(id, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Dépense type non trouvée : " + id));

        depenseType.setNom(request.nom());
        depenseType.setMontant(request.montant());
        depenseType.setCategorie(request.categorie());
        depenseType.setTypeDepense(request.typeDepense());
        depenseType.setFrequence(request.frequence());

        DepenseType saved = depenseTypeRepository.save(depenseType);
        return toDepenseTypeDto(saved);
    }

    /**
     * Récupère toutes les dépenses types d'un mois type (actives et inactives).
     *
     * @param moisTypeId l'identifiant du mois type
     * @return liste des dépenses types triées par catégorie et nom
     * @throws ResourceNotFoundException si le mois type n'appartient pas au foyer courant
     */
    @Transactional(readOnly = true)
    public List<DepenseTypeDto> listerDepensesType(UUID moisTypeId) {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        // Vérifie que le mois type appartient au foyer courant
        moisTypeRepository.findByIdAndHouseholdId(moisTypeId, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Mois type non trouvé : " + moisTypeId));
        return depenseTypeRepository.findByMoisTypeIdAndHouseholdId(moisTypeId, householdId)
                .stream().map(this::toDepenseTypeDto).toList();
    }

    /**
     * Bascule l'état actif/inactif d'une dépense type.
     * Une dépense inactive est exclue des calculs de projection.
     *
     * @param id l'identifiant de la dépense type
     * @return la dépense type avec son nouvel état
     * @throws ResourceNotFoundException si la dépense type n'appartient pas au foyer courant
     */
    @Transactional
    public DepenseTypeDto basculerActivation(UUID id) {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        DepenseType depenseType = depenseTypeRepository.findByIdAndHouseholdId(id, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Dépense type non trouvée : " + id));

        // Inverse l'état actif
        depenseType.setActif(!depenseType.getActif());
        DepenseType saved = depenseTypeRepository.save(depenseType);
        return toDepenseTypeDto(saved);
    }

    /**
     * Active ou désactive une dépense type.
     * Une dépense inactive est exclue des calculs de projection.
     *
     * @param id    l'identifiant de la dépense type
     * @param actif le nouvel état
     * @return la dépense type mise à jour
     * @throws ResourceNotFoundException si la dépense type n'appartient pas au foyer courant
     */
    @Transactional
    public DepenseTypeDto changerEtatDepenseType(UUID id, boolean actif) {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        DepenseType depenseType = depenseTypeRepository.findByIdAndHouseholdId(id, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Dépense type non trouvée : " + id));

        depenseType.setActif(actif);
        DepenseType saved = depenseTypeRepository.save(depenseType);
        return toDepenseTypeDto(saved);
    }

    /**
     * Supprime une dépense type.
     *
     * @param id l'identifiant de la dépense type
     * @throws ResourceNotFoundException si la dépense type n'appartient pas au foyer courant
     */
    @Transactional
    public void supprimerDepenseType(UUID id) {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        DepenseType depenseType = depenseTypeRepository.findByIdAndHouseholdId(id, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Dépense type non trouvée : " + id));
        depenseTypeRepository.delete(depenseType);
    }

    // =========================================================================
    // Méthodes de conversion en DTOs
    // =========================================================================

    /**
     * Convertit une entité MoisType en DTO avec les totaux calculés.
     *
     * @param moisType      l'entité à convertir
     * @param inclureDetail si true, inclut la liste des dépenses types
     * @return le DTO avec totaux calculés
     */
    private MoisTypeDto toMoisTypeDto(MoisType moisType, boolean inclureDetail) {
        long totalRevenusCents = moisType.getRevenus() + moisType.getAutresRevenus();

        // Calcul des totaux par type (fixes et variables)
        long totalFixesCents = 0L;
        long totalVariablesCents = 0L;

        for (DepenseType dt : moisType.getDepenses()) {
            if (Boolean.TRUE.equals(dt.getActif())) {
                long mensuel = calculerMontantMensuel(dt.getMontant(), dt.getFrequence());
                if (TypeDepense.FIXE.equals(dt.getTypeDepense())) {
                    totalFixesCents += mensuel;
                } else {
                    totalVariablesCents += mensuel;
                }
            }
        }

        long totalDepensesCents = totalFixesCents + totalVariablesCents;
        long epargnePossibleCents = totalRevenusCents - totalDepensesCents;
        double tauxEpargne = totalRevenusCents > 0
                ? Math.round((double) epargnePossibleCents / totalRevenusCents * 10000.0) / 100.0
                : 0.0;

        // Inclure la liste des dépenses uniquement si demandé (pour le détail)
        List<DepenseTypeDto> depenses = inclureDetail
                ? moisType.getDepenses().stream().map(this::toDepenseTypeDto).toList()
                : null;

        return new MoisTypeDto(
                moisType.getId(),
                moisType.getNom(),
                moisType.getAnnee(),
                moisType.getRevenus(),
                moisType.getAutresRevenus(),
                totalRevenusCents,
                totalFixesCents,
                totalVariablesCents,
                totalDepensesCents,
                epargnePossibleCents,
                tauxEpargne,
                depenses,
                moisType.getCreatedAt(),
                moisType.getUpdatedAt()
        );
    }

    /**
     * Convertit une entité DepenseType en DTO avec le montant mensuel calculé.
     *
     * @param dt l'entité à convertir
     * @return le DTO avec montant mensuel équivalent
     */
    private DepenseTypeDto toDepenseTypeDto(DepenseType dt) {
        long montantMensuel = calculerMontantMensuel(dt.getMontant(), dt.getFrequence());
        return new DepenseTypeDto(
                dt.getId(),
                dt.getMoisType().getId(),
                dt.getNom(),
                dt.getMontant(),
                montantMensuel,
                dt.getCategorie(),
                dt.getTypeDepense(),
                dt.getFrequence(),
                dt.getActif(),
                dt.getCreatedAt(),
                dt.getUpdatedAt()
        );
    }

    /**
     * Calcule le montant mensuel équivalent en fonction de la fréquence.
     * MENSUELLE : montant intact
     * TRIMESTRIELLE : montant / 3
     * ANNUELLE : montant / 12
     *
     * @param montant   le montant pour la période
     * @param frequence la fréquence de récurrence
     * @return le montant mensuel en centimes (arrondi à l'entier inférieur)
     */
    private long calculerMontantMensuel(Long montant, FrequenceDepense frequence) {
        return switch (frequence) {
            case MENSUELLE -> montant;
            case TRIMESTRIELLE -> montant / 3;
            case ANNUELLE -> montant / 12;
        };
    }

    /**
     * Calcule le montant mensuel équivalent d'une dépense type.
     * Méthode publique utilisée par ProjectionService pour les calculs de projection.
     *
     * @param depenseType la dépense type
     * @return le montant mensuel en centimes
     */
    public long calculerMontantMensuelDepense(DepenseType depenseType) {
        return calculerMontantMensuel(depenseType.getMontant(), depenseType.getFrequence());
    }

    /**
     * Calcule les totaux mensuels fixes et variables à partir d'une liste de dépenses actives.
     * Méthode publique utilisée par ProjectionService.
     *
     * @param depensesActives liste de dépenses actives (le filtrage actif doit être fait en amont)
     * @return tableau : [0] = totalFixesCents, [1] = totalVariablesCents
     */
    public long[] calculerTotauxMensuels(List<DepenseType> depensesActives) {
        long totalFixesCents = 0L;
        long totalVariablesCents = 0L;

        for (DepenseType dt : depensesActives) {
            long mensuel = calculerMontantMensuel(dt.getMontant(), dt.getFrequence());
            if (TypeDepense.FIXE.equals(dt.getTypeDepense())) {
                totalFixesCents += mensuel;
            } else {
                totalVariablesCents += mensuel;
            }
        }

        return new long[]{totalFixesCents, totalVariablesCents};
    }
}
