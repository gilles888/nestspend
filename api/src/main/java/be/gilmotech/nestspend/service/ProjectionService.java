package be.gilmotech.nestspend.service;

import be.gilmotech.nestspend.domain.entity.DepenseType;
import be.gilmotech.nestspend.domain.entity.FutureEvent;
import be.gilmotech.nestspend.domain.entity.MoisType;
import be.gilmotech.nestspend.domain.enums.TransactionType;
import be.gilmotech.nestspend.domain.repository.DepenseTypeRepository;
import be.gilmotech.nestspend.domain.repository.FutureEventRepository;
import be.gilmotech.nestspend.domain.repository.MoisTypeRepository;
import be.gilmotech.nestspend.domain.repository.TransactionRepository;
import be.gilmotech.nestspend.dto.moistype.ProjectionAnnuelleDto;
import be.gilmotech.nestspend.dto.moistype.ProjectionMoisDto;
import be.gilmotech.nestspend.dto.projection.AnnualProjectionResponse;
import be.gilmotech.nestspend.dto.projection.AnnualSavingsResponse;
import be.gilmotech.nestspend.dto.projection.MonthlyExpenseProjection;
import be.gilmotech.nestspend.dto.projection.MonthlySavingsProjection;
import be.gilmotech.nestspend.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service de projection des dépenses mensuelles et annuelles.
 * Combine les données réelles des transactions passées avec les événements futurs
 * planifiés pour produire une vision complète de l'année.
 *
 * <p>Règle de calcul selon le mois :</p>
 * <ul>
 *   <li>Mois passé : données réelles des transactions uniquement.</li>
 *   <li>Mois courant : données réelles des transactions + contribution des événements futurs.</li>
 *   <li>Mois futur : projection basée sur les événements récurrents planifiés uniquement.</li>
 * </ul>
 */
@Service
public class ProjectionService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final TransactionRepository transactionRepository;
    private final FutureEventRepository futureEventRepository;
    private final MoisTypeRepository moisTypeRepository;
    private final DepenseTypeRepository depenseTypeRepository;
    private final CurrentUserService currentUserService;
    private final ProjectionHelper projectionHelper;
    private final MoisTypeService moisTypeService;

    public ProjectionService(TransactionRepository transactionRepository,
                             FutureEventRepository futureEventRepository,
                             MoisTypeRepository moisTypeRepository,
                             DepenseTypeRepository depenseTypeRepository,
                             CurrentUserService currentUserService,
                             ProjectionHelper projectionHelper,
                             MoisTypeService moisTypeService) {
        this.transactionRepository = transactionRepository;
        this.futureEventRepository = futureEventRepository;
        this.moisTypeRepository = moisTypeRepository;
        this.depenseTypeRepository = depenseTypeRepository;
        this.currentUserService = currentUserService;
        this.projectionHelper = projectionHelper;
        this.moisTypeService = moisTypeService;
    }

    /**
     * Génère la projection annuelle des dépenses et revenus pour une année donnée.
     * Pour les mois passés, les données réelles des transactions sont utilisées.
     * Pour les mois futurs, les événements récurrents planifiés sont utilisés.
     * Le mois courant combine données réelles + projection des événements futurs restants.
     *
     * @param year l'année à projeter (ex : 2025)
     * @return la projection annuelle complète avec 12 points de données
     * @throws IllegalArgumentException si l'année est invalide (avant 2000 ou plus de 10 ans dans le futur)
     */
    @Transactional(readOnly = true)
    public AnnualProjectionResponse getAnnualProjection(int year) {
        validerAnnee(year);

        UUID householdId = currentUserService.getCurrentHouseholdId();
        YearMonth currentMonth = YearMonth.from(LocalDate.now());

        // Récupère les événements futurs actifs pour l'année complète
        List<FutureEvent> futureEvents = chargerEvenementsFuturs(householdId, year);

        List<MonthlyExpenseProjection> months = new ArrayList<>(12);
        long totalActualIncome = 0L;
        long totalActualExpenses = 0L;
        long totalProjectedIncome = 0L;
        long totalProjectedExpenses = 0L;

        for (int monthNum = 1; monthNum <= 12; monthNum++) {
            YearMonth ym = YearMonth.of(year, monthNum);
            String monthLabel = ym.format(MONTH_FORMATTER);
            LocalDate monthStart = ym.atDay(1);
            LocalDate monthEnd = ym.atEndOfMonth();

            long income;
            long expenses;
            boolean isActual;

            if (ym.isBefore(currentMonth)) {
                // Mois passé : données réelles uniquement
                income = sommerTransactions(householdId, TransactionType.INCOME, monthStart, monthEnd);
                expenses = sommerTransactions(householdId, TransactionType.EXPENSE, monthStart, monthEnd);
                isActual = true;

                totalActualIncome += income;
                totalActualExpenses += expenses;
                totalProjectedIncome += income;
                totalProjectedExpenses += expenses;

            } else if (ym.equals(currentMonth)) {
                // Mois courant : données réelles + projection des événements futurs
                long actualIncome = sommerTransactions(householdId, TransactionType.INCOME, monthStart, monthEnd);
                long actualExpenses = sommerTransactions(householdId, TransactionType.EXPENSE, monthStart, monthEnd);

                // Contribution des événements futurs pour le mois courant
                long futureIncome = 0L;
                long futureExpenses = 0L;
                for (FutureEvent event : futureEvents) {
                    long occurrences = projectionHelper.countOccurrencesInMonth(event, ym);
                    long amount = event.getAmountCents() * occurrences;
                    if (event.getType() == TransactionType.INCOME) {
                        futureIncome += amount;
                    } else {
                        futureExpenses += amount;
                    }
                }

                income = actualIncome + futureIncome;
                expenses = actualExpenses + futureExpenses;
                isActual = false; // Données mixtes : réelles + projetées

                totalActualIncome += actualIncome;
                totalActualExpenses += actualExpenses;
                totalProjectedIncome += income;
                totalProjectedExpenses += expenses;

            } else {
                // Mois futur : projection basée sur les événements planifiés uniquement
                income = 0L;
                expenses = 0L;

                for (FutureEvent event : futureEvents) {
                    long occurrences = projectionHelper.countOccurrencesInMonth(event, ym);
                    long amount = event.getAmountCents() * occurrences;
                    if (event.getType() == TransactionType.INCOME) {
                        income += amount;
                    } else {
                        expenses += amount;
                    }
                }

                isActual = false;
                totalProjectedIncome += income;
                totalProjectedExpenses += expenses;
            }

            months.add(new MonthlyExpenseProjection(
                    monthLabel,
                    income,
                    expenses,
                    income - expenses,
                    isActual
            ));
        }

        return new AnnualProjectionResponse(
                year,
                totalActualIncome,
                totalActualExpenses,
                totalProjectedIncome,
                totalProjectedExpenses,
                totalProjectedIncome - totalProjectedExpenses,
                months
        );
    }

    /**
     * Génère la projection mensuelle détaillée pour une année donnée.
     * Alias vers {@link #getAnnualProjection(int)} — expose les mêmes données
     * sous un endpoint dédié pour la clarté de l'API.
     *
     * @param year l'année à projeter
     * @return la projection annuelle avec les 12 mois
     */
    @Transactional(readOnly = true)
    public AnnualProjectionResponse getMonthlyProjection(int year) {
        // La logique est identique à la projection annuelle :
        // les deux retournent les 12 mois avec données réelles/projetées.
        return getAnnualProjection(year);
    }

    /**
     * Calcule l'épargne mois par mois et le cumul pour une année donnée.
     * L'épargne d'un mois = revenus - dépenses.
     * Le cumul représente la somme des épargnes depuis janvier.
     *
     * @param year l'année cible
     * @return la projection d'épargne annuelle avec cumul mensuel
     * @throws IllegalArgumentException si l'année est invalide
     */
    @Transactional(readOnly = true)
    public AnnualSavingsResponse getSavingsProjection(int year) {
        validerAnnee(year);

        // Réutilise la logique de projection annuelle pour obtenir les données de base
        AnnualProjectionResponse annualProjection = getAnnualProjection(year);

        List<MonthlySavingsProjection> savingsMonths = new ArrayList<>(12);
        long cumulativeSavings = 0L;
        long totalActualSavings = 0L;
        long totalProjectedSavings = 0L;

        for (MonthlyExpenseProjection month : annualProjection.months()) {
            long monthlySavings = month.netCents(); // revenus - dépenses
            cumulativeSavings += monthlySavings;
            totalProjectedSavings += monthlySavings;

            if (month.isActual()) {
                totalActualSavings += monthlySavings;
            }

            savingsMonths.add(new MonthlySavingsProjection(
                    month.month(),
                    month.incomeCents(),
                    month.expenseCents(),
                    monthlySavings,
                    cumulativeSavings,
                    month.isActual()
            ));
        }

        return new AnnualSavingsResponse(
                year,
                totalActualSavings,
                totalProjectedSavings,
                savingsMonths
        );
    }

    /**
     * Charge les événements futurs actifs pour une année donnée.
     */
    private List<FutureEvent> chargerEvenementsFuturs(UUID householdId, int year) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        return futureEventRepository.findActiveEventsInRange(householdId, yearStart, yearEnd);
    }

    /**
     * Somme les montants des transactions d'un type donné pour le foyer et la plage de dates.
     * Retourne 0 si aucune transaction n'existe (protège contre les null JPQL).
     */
    private long sommerTransactions(UUID householdId, TransactionType type,
                                    LocalDate start, LocalDate end) {
        Long result = transactionRepository.sumAmountByHouseholdAndTypeAndDateRange(
                householdId, type, start, end);
        return result != null ? result : 0L;
    }

    /**
     * Valide que l'année est dans une plage raisonnable.
     *
     * @param year l'année à valider
     * @throws IllegalArgumentException si l'année est hors plage
     */
    private void validerAnnee(int year) {
        int currentYear = Year.now().getValue();
        if (year < 2000 || year > currentYear + 10) {
            throw new IllegalArgumentException(
                    "Année invalide : " + year + ". Doit être comprise entre 2000 et " + (currentYear + 10));
        }
    }

    // ==========================================================================
    // Projections basées sur le mois type
    // ==========================================================================

    /**
     * Génère la projection annuelle des 12 mois à partir du mois type de l'année donnée.
     * Si aucun mois type n'existe pour cette année, retourne une projection vide (tous à zéro).
     * L'épargne cumulée est calculée mois par mois.
     *
     * @param year l'année cible (ex : 2026)
     * @return la projection annuelle avec 12 mois et les totaux annuels
     * @throws IllegalArgumentException si l'année est invalide
     */
    @Transactional(readOnly = true)
    public ProjectionAnnuelleDto getProjectionMoisType(int year) {
        validerAnnee(year);
        UUID householdId = currentUserService.getCurrentHouseholdId();

        // Charge le premier mois type de l'année (s'il existe)
        Optional<MoisType> moisTypeOpt = trouverMoisTypePourAnnee(householdId, year);

        if (moisTypeOpt.isEmpty()) {
            // Aucun mois type défini : retour d'une projection vide à zéro
            return construireProjectionVide(year);
        }

        MoisType moisType = moisTypeOpt.get();

        // Récupère les dépenses actives du mois type pour les calculs
        List<DepenseType> depensesActives = depenseTypeRepository
                .findActiveByMoisTypeIdAndHouseholdId(moisType.getId(), householdId);

        // Calcul des totaux mensuels
        long[] totaux = moisTypeService.calculerTotauxMensuels(depensesActives);
        long totalFixesCents = totaux[0];
        long totalVariablesCents = totaux[1];
        long totalDepensesMensuelles = totalFixesCents + totalVariablesCents;
        long totalRevenusMensuels = moisType.getRevenus() + moisType.getAutresRevenus();
        long epargneMensuelle = totalRevenusMensuels - totalDepensesMensuelles;

        // Construction des 12 mois avec épargne cumulée
        List<ProjectionMoisDto> mois = new ArrayList<>(12);
        long epargneCumulee = 0L;
        long totalRevenusCents = 0L;
        long totalDepensesCents = 0L;
        long totalEpargneCents = 0L;

        for (int numMois = 1; numMois <= 12; numMois++) {
            YearMonth ym = YearMonth.of(year, numMois);
            String moisLabel = ym.format(MONTH_FORMATTER);

            epargneCumulee += epargneMensuelle;
            totalRevenusCents += totalRevenusMensuels;
            totalDepensesCents += totalDepensesMensuelles;
            totalEpargneCents += epargneMensuelle;

            mois.add(new ProjectionMoisDto(
                    moisLabel,
                    totalRevenusMensuels,
                    totalFixesCents,
                    totalVariablesCents,
                    totalDepensesMensuelles,
                    epargneMensuelle,
                    epargneCumulee
            ));
        }

        // Taux d'épargne annuel
        double tauxEpargne = totalRevenusCents > 0
                ? BigDecimal.valueOf((double) totalEpargneCents / totalRevenusCents * 100)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue()
                : 0.0;

        return new ProjectionAnnuelleDto(
                year,
                mois,
                totalRevenusCents,
                totalDepensesCents,
                totalEpargneCents,
                tauxEpargne
        );
    }

    /**
     * Calcule le total de l'épargne possible sur l'année à partir du mois type.
     * Retourne 0 si aucun mois type n'est défini pour l'année.
     *
     * @param year l'année cible
     * @return la projection annuelle (on peut en extraire le totalEpargneCents)
     * @throws IllegalArgumentException si l'année est invalide
     */
    @Transactional(readOnly = true)
    public ProjectionAnnuelleDto getEpargneMoisType(int year) {
        // On réutilise la projection complète, l'épargne totale est incluse
        return getProjectionMoisType(year);
    }

    /**
     * Recherche le premier mois type disponible pour un foyer et une année donnée.
     * Si plusieurs mois types existent pour l'année, le plus ancien est utilisé.
     *
     * @param householdId l'identifiant du foyer
     * @param year        l'année de référence
     * @return le mois type s'il existe
     */
    private Optional<MoisType> trouverMoisTypePourAnnee(UUID householdId, int year) {
        List<MoisType> candidats = moisTypeRepository.findByHouseholdIdAndAnneeWithDepenses(householdId, year);
        return candidats.isEmpty() ? Optional.empty() : Optional.of(candidats.get(0));
    }

    /**
     * Construit une projection annuelle vide (tous les montants à zéro).
     * Retournée quand aucun mois type n'est défini pour l'année demandée.
     *
     * @param year l'année de la projection
     * @return une projection avec 12 mois à zéro
     */
    private ProjectionAnnuelleDto construireProjectionVide(int year) {
        List<ProjectionMoisDto> mois = new ArrayList<>(12);
        for (int numMois = 1; numMois <= 12; numMois++) {
            YearMonth ym = YearMonth.of(year, numMois);
            mois.add(new ProjectionMoisDto(
                    ym.format(MONTH_FORMATTER),
                    0L, 0L, 0L, 0L, 0L, 0L
            ));
        }
        return new ProjectionAnnuelleDto(year, mois, 0L, 0L, 0L, 0.0);
    }
}
