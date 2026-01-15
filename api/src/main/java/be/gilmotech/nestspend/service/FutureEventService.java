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
     * Create a new future event.
     */
    @Transactional
    public FutureEventResponse createFutureEvent(FutureEventCreateRequest request) {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Household not found"));

        FutureEvent event = FutureEvent.builder()
                .household(household)
                .name(request.name())
                .amountCents(request.amountCents())
                .type(TransactionType.valueOf(request.type()))
                .periodicity(Periodicity.valueOf(request.periodicity()))
                .startDate(request.startDate())
                .endDate(request.endDate())
                .build();

        FutureEvent saved = futureEventRepository.save(event);
        return toResponse(saved);
    }

    /**
     * Update an existing future event.
     */
    @Transactional
    public FutureEventResponse updateFutureEvent(UUID id, FutureEventUpdateRequest request) {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        FutureEvent event = futureEventRepository.findByIdAndHouseholdId(id, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Future event not found: " + id));

        event.setName(request.name());
        event.setAmountCents(request.amountCents());
        event.setType(TransactionType.valueOf(request.type()));
        event.setPeriodicity(Periodicity.valueOf(request.periodicity()));
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
        Long initialBalance = currentIncome - currentExpenses;

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
     * Count how many times an event occurs in a given month based on its periodicity.
     */
    private long countOccurrencesInMonth(FutureEvent event, YearMonth month) {
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();

        // Check if event is active during this month
        if (event.getStartDate().isAfter(monthEnd)) {
            return 0;
        }
        if (event.getEndDate() != null && event.getEndDate().isBefore(monthStart)) {
            return 0;
        }

        return switch (event.getPeriodicity()) {
            case WEEKLY -> 4; // Approximation: 4 weeks per month
            case MONTHLY -> 1;
            case QUARTERLY -> {
                int eventMonth = event.getStartDate().getMonthValue();
                int currentMonth = month.getMonthValue();
                // Event occurs if (currentMonth - eventMonth) is divisible by 3
                int diff = (currentMonth - eventMonth + 12) % 12;
                yield (diff % 3 == 0) ? 1 : 0;
            }
            case YEARLY -> {
                // Event occurs only in the same month as start date
                yield (event.getStartDate().getMonthValue() == month.getMonthValue()) ? 1 : 0;
            }
        };
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
