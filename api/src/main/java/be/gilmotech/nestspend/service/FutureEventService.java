package be.gilmotech.nestspend.service;

import be.gilmotech.nestspend.domain.entity.FutureEvent;
import be.gilmotech.nestspend.domain.entity.Household;
import be.gilmotech.nestspend.domain.enums.Periodicity;
import be.gilmotech.nestspend.domain.enums.TransactionType;
import be.gilmotech.nestspend.domain.repository.FutureEventRepository;
import be.gilmotech.nestspend.domain.repository.HouseholdRepository;
import be.gilmotech.nestspend.domain.repository.TransactionRepository;
import be.gilmotech.nestspend.dto.futureevent.*;
import be.gilmotech.nestspend.exception.ResourceNotFoundException;
import be.gilmotech.nestspend.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing future events and budget projections.
 */
@Service
public class FutureEventService {

    private final FutureEventRepository futureEventRepository;
    private final HouseholdRepository householdRepository;
    private final TransactionRepository transactionRepository;
    private final CurrentUserService currentUserService;

    public FutureEventService(FutureEventRepository futureEventRepository,
                              HouseholdRepository householdRepository,
                              TransactionRepository transactionRepository,
                              CurrentUserService currentUserService) {
        this.futureEventRepository = futureEventRepository;
        this.householdRepository = householdRepository;
        this.transactionRepository = transactionRepository;
        this.currentUserService = currentUserService;
    }

    /**
     * Get all future events for the current user's household.
     */
    @Transactional(readOnly = true)
    public List<FutureEventResponse> getAllFutureEvents() {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        return futureEventRepository.findByHouseholdId(householdId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Get a future event by ID.
     */
    @Transactional(readOnly = true)
    public FutureEventResponse getFutureEvent(UUID id) {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        FutureEvent event = futureEventRepository.findByIdAndHouseholdId(id, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Future event not found: " + id));
        return toResponse(event);
    }

    /**
     * Crée un nouvel événement futur pour le foyer de l'utilisateur courant.
     */
    @Transactional
    public FutureEventResponse createFutureEvent(FutureEventCreateRequest request) {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Household not found"));

        // Validation de la cohérence des dates
        validerCoherenceDates(request.startDate(), request.endDate());

        FutureEvent event = FutureEvent.builder()
                .household(household)
                .name(request.name())
                .amountCents(request.amountCents())
                .type(parseTransactionType(request.type()))
                .periodicity(parsePeriodicity(request.periodicity()))
                .startDate(request.startDate())
                .endDate(request.endDate())
                .build();

        FutureEvent saved = futureEventRepository.save(event);
        return toResponse(saved);
    }

    /**
     * Met à jour un événement futur existant.
     */
    @Transactional
    public FutureEventResponse updateFutureEvent(UUID id, FutureEventUpdateRequest request) {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        FutureEvent event = futureEventRepository.findByIdAndHouseholdId(id, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Future event not found: " + id));

        // Validation de la cohérence des dates
        validerCoherenceDates(request.startDate(), request.endDate());

        event.setName(request.name());
        event.setAmountCents(request.amountCents());
        event.setType(parseTransactionType(request.type()));
        event.setPeriodicity(parsePeriodicity(request.periodicity()));
        event.setStartDate(request.startDate());
        event.setEndDate(request.endDate());

        FutureEvent saved = futureEventRepository.save(event);
        return toResponse(saved);
    }

    /**
     * Delete a future event.
     */
    @Transactional
    public void deleteFutureEvent(UUID id) {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        FutureEvent event = futureEventRepository.findByIdAndHouseholdId(id, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Future event not found: " + id));
        futureEventRepository.delete(event);
    }

    /**
     * Calculate budget projections for a given period.
     *
     * @param months number of months to project (1-12)
     * @return projection response with data points
     */
    @Transactional(readOnly = true)
    public ProjectionResponse getProjections(int months) {
        if (months < 1 || months > 12) {
            throw new IllegalArgumentException("Months must be between 1 and 12");
        }

        UUID householdId = currentUserService.getCurrentHouseholdId();
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.withDayOfMonth(1);
        LocalDate endDate = startDate.plusMonths(months).minusDays(1);

        // Get current month's actual data for initial balance calculation
        YearMonth currentMonth = YearMonth.from(today);
        LocalDate monthStart = currentMonth.atDay(1);
        LocalDate monthEnd = currentMonth.atEndOfMonth();

        Long currentIncome = transactionRepository.sumAmountByHouseholdAndTypeAndDateRange(
                householdId, TransactionType.INCOME, monthStart, monthEnd);
        Long currentExpenses = transactionRepository.sumAmountByHouseholdAndTypeAndDateRange(
                householdId, TransactionType.EXPENSE, monthStart, monthEnd);
        // Handle potential null values (though COALESCE should prevent this)
        Long initialBalance = (currentIncome != null ? currentIncome : 0L) - (currentExpenses != null ? currentExpenses : 0L);

        // Get active future events
        List<FutureEvent> futureEvents = futureEventRepository.findActiveEventsInRange(
                householdId, startDate, endDate);

        // Calculate projections for each month
        List<ProjectionDataPoint> dataPoints = new ArrayList<>();
        Long runningBalance = initialBalance;

        for (int i = 0; i < months; i++) {
            YearMonth month = YearMonth.from(startDate.plusMonths(i));
            LocalDate monthEndDate = month.atEndOfMonth();

            long monthlyIncome = 0L;
            long monthlyExpenses = 0L;

            // Calculate contributions from future events
            for (FutureEvent event : futureEvents) {
                long occurrences = countOccurrencesInMonth(event, month);
                long amount = event.getAmountCents() * occurrences;

                if (event.getType() == TransactionType.INCOME) {
                    monthlyIncome += amount;
                } else {
                    monthlyExpenses += amount;
                }
            }

            runningBalance = runningBalance + monthlyIncome - monthlyExpenses;

            dataPoints.add(new ProjectionDataPoint(
                    monthEndDate,
                    monthlyIncome,
                    monthlyExpenses,
                    runningBalance
            ));
        }

        Long finalBalance = dataPoints.isEmpty() ? initialBalance : 
                dataPoints.get(dataPoints.size() - 1).projectedBalanceCents();

        return new ProjectionResponse(
                startDate,
                endDate,
                initialBalance,
                finalBalance,
                dataPoints
        );
    }

    /**
     * Compte le nombre d'occurrences d'un événement dans un mois donné selon sa périodicité.
     * Pour la périodicité WEEKLY, on compte précisément le nombre de semaines dans le mois
     * au lieu d'utiliser une approximation fixe de 4.
     */
    private long countOccurrencesInMonth(FutureEvent event, YearMonth month) {
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();

        // Vérifie si l'événement est actif pendant ce mois
        if (event.getStartDate().isAfter(monthEnd)) {
            return 0;
        }
        if (event.getEndDate() != null && event.getEndDate().isBefore(monthStart)) {
            return 0;
        }

        return switch (event.getPeriodicity()) {
            case WEEKLY -> {
                // Calcul précis du nombre d'occurrences hebdomadaires dans le mois
                // On prend le jour de la semaine de la date de départ comme référence
                LocalDate effectiveStart = event.getStartDate().isBefore(monthStart)
                        ? monthStart
                        : event.getStartDate();
                LocalDate effectiveEnd = event.getEndDate() != null && event.getEndDate().isBefore(monthEnd)
                        ? event.getEndDate()
                        : monthEnd;

                // Nombre de jours dans l'intervalle effectif, divisé par 7 (arrondi supérieur)
                long days = effectiveStart.until(effectiveEnd, ChronoUnit.DAYS) + 1;
                yield (days + 6) / 7; // équivalent à ceil(days / 7)
            }
            case MONTHLY -> 1;
            case QUARTERLY -> {
                int eventMonth = event.getStartDate().getMonthValue();
                int currentMonth = month.getMonthValue();
                // L'événement se produit si (currentMonth - eventMonth) est divisible par 3
                int diff = (currentMonth - eventMonth + 12) % 12;
                yield (diff % 3 == 0) ? 1 : 0;
            }
            case YEARLY -> {
                // L'événement se produit uniquement dans le même mois que la date de début
                yield (event.getStartDate().getMonthValue() == month.getMonthValue()) ? 1 : 0;
            }
        };
    }

    /**
     * Valide que la date de fin est postérieure à la date de début si elle est fournie.
     */
    private void validerCoherenceDates(LocalDate startDate, LocalDate endDate) {
        if (endDate != null && !endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("La date de fin doit être postérieure à la date de début");
        }
    }

    /**
     * Parse le type de transaction avec un message d'erreur explicite.
     */
    private TransactionType parseTransactionType(String type) {
        try {
            return TransactionType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Type de transaction invalide : " + type + ". Valeurs acceptées : INCOME, EXPENSE");
        }
    }

    /**
     * Parse la périodicité avec un message d'erreur explicite.
     */
    private Periodicity parsePeriodicity(String periodicity) {
        try {
            return Periodicity.valueOf(periodicity);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Périodicité invalide : " + periodicity + ". Valeurs acceptées : WEEKLY, MONTHLY, QUARTERLY, YEARLY");
        }
    }

    private FutureEventResponse toResponse(FutureEvent event) {
        return new FutureEventResponse(
                event.getId(),
                event.getName(),
                event.getAmountCents(),
                event.getType().name(),
                event.getPeriodicity().name(),
                event.getStartDate(),
                event.getEndDate()
        );
    }
}
