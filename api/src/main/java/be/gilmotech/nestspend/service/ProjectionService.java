package be.gilmotech.nestspend.service;

import be.gilmotech.nestspend.domain.entity.FutureEvent;
import be.gilmotech.nestspend.domain.enums.TransactionType;
import be.gilmotech.nestspend.domain.repository.FutureEventRepository;
import be.gilmotech.nestspend.domain.repository.TransactionRepository;
import be.gilmotech.nestspend.dto.projection.AnnualProjectionResponse;
import be.gilmotech.nestspend.dto.projection.MonthlyExpenseProjection;
import be.gilmotech.nestspend.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service de projection des dépenses mensuelles et annuelles.
 * Combine les données réelles des transactions passées avec les événements futurs
 * planifiés pour produire une vision complète de l'année.
 */
@Service
public class ProjectionService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final TransactionRepository transactionRepository;
    private final FutureEventRepository futureEventRepository;
    private final CurrentUserService currentUserService;

    public ProjectionService(TransactionRepository transactionRepository,
                             FutureEventRepository futureEventRepository,
                             CurrentUserService currentUserService) {
        this.transactionRepository = transactionRepository;
        this.futureEventRepository = futureEventRepository;
        this.currentUserService = currentUserService;
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
        int currentYear = Year.now().getValue();
        if (year < 2000 || year > currentYear + 10) {
            throw new IllegalArgumentException(
                    "Année invalide : " + year + ". Doit être comprise entre 2000 et " + (currentYear + 10));
        }

        UUID householdId = currentUserService.getCurrentHouseholdId();
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);

        // Récupère les événements futurs actifs pour l'année complète
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        List<FutureEvent> futureEvents = futureEventRepository.findActiveEventsInRange(
                householdId, yearStart, yearEnd);

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
                Long rawIncome = transactionRepository.sumAmountByHouseholdAndTypeAndDateRange(
                        householdId, TransactionType.INCOME, monthStart, monthEnd);
                Long rawExpenses = transactionRepository.sumAmountByHouseholdAndTypeAndDateRange(
                        householdId, TransactionType.EXPENSE, monthStart, monthEnd);

                income = rawIncome != null ? rawIncome : 0L;
                expenses = rawExpenses != null ? rawExpenses : 0L;
                isActual = true;

                totalActualIncome += income;
                totalActualExpenses += expenses;
            } else if (ym.equals(currentMonth)) {
                // Mois courant : données réelles jusqu'à aujourd'hui + projection des événements futurs
                Long rawIncome = transactionRepository.sumAmountByHouseholdAndTypeAndDateRange(
                        householdId, TransactionType.INCOME, monthStart, monthEnd);
                Long rawExpenses = transactionRepository.sumAmountByHouseholdAndTypeAndDateRange(
                        householdId, TransactionType.EXPENSE, monthStart, monthEnd);

                income = rawIncome != null ? rawIncome : 0L;
                expenses = rawExpenses != null ? rawExpenses : 0L;

                // Ajout des événements futurs pour le mois courant
                for (FutureEvent event : futureEvents) {
                    long occurrences = countOccurrencesInMonth(event, ym);
                    long amount = event.getAmountCents() * occurrences;
                    if (event.getType() == TransactionType.INCOME) {
                        income += amount;
                    } else {
                        expenses += amount;
                    }
                }

                isActual = false; // Mois en cours : données mixtes

                totalActualIncome += rawIncome != null ? rawIncome : 0L;
                totalActualExpenses += rawExpenses != null ? rawExpenses : 0L;
            } else {
                // Mois futur : projection basée sur les événements planifiés
                income = 0L;
                expenses = 0L;

                for (FutureEvent event : futureEvents) {
                    long occurrences = countOccurrencesInMonth(event, ym);
                    long amount = event.getAmountCents() * occurrences;
                    if (event.getType() == TransactionType.INCOME) {
                        income += amount;
                    } else {
                        expenses += amount;
                    }
                }

                isActual = false;
            }

            totalProjectedIncome += income;
            totalProjectedExpenses += expenses;

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
     * Compte le nombre d'occurrences d'un événement futur dans un mois donné.
     * Identique à la logique de FutureEventService pour assurer la cohérence.
     */
    private long countOccurrencesInMonth(FutureEvent event, YearMonth month) {
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();

        if (event.getStartDate().isAfter(monthEnd)) {
            return 0;
        }
        if (event.getEndDate() != null && event.getEndDate().isBefore(monthStart)) {
            return 0;
        }

        return switch (event.getPeriodicity()) {
            case WEEKLY -> {
                LocalDate effectiveStart = event.getStartDate().isBefore(monthStart)
                        ? monthStart : event.getStartDate();
                LocalDate effectiveEnd = event.getEndDate() != null && event.getEndDate().isBefore(monthEnd)
                        ? event.getEndDate() : monthEnd;
                long days = effectiveStart.until(effectiveEnd, java.time.temporal.ChronoUnit.DAYS) + 1;
                yield (days + 6) / 7;
            }
            case MONTHLY -> 1;
            case QUARTERLY -> {
                int eventMonth = event.getStartDate().getMonthValue();
                int currentMonthVal = month.getMonthValue();
                int diff = (currentMonthVal - eventMonth + 12) % 12;
                yield (diff % 3 == 0) ? 1 : 0;
            }
            case YEARLY ->
                    (event.getStartDate().getMonthValue() == month.getMonthValue()) ? 1 : 0;
        };
    }
}
