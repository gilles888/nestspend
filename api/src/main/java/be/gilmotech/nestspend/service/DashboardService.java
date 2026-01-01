package be.gilmotech.nestspend.service;

import be.gilmotech.nestspend.domain.enums.TransactionType;
import be.gilmotech.nestspend.domain.repository.TransactionRepository;
import be.gilmotech.nestspend.dto.dashboard.CategoryExpenseResponse;
import be.gilmotech.nestspend.dto.dashboard.DashboardResponse;
import be.gilmotech.nestspend.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * Service for dashboard aggregations with household scoping.
 */
@Service
public class DashboardService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final TransactionRepository transactionRepository;
    private final CurrentUserService currentUserService;

    public DashboardService(TransactionRepository transactionRepository,
                            CurrentUserService currentUserService) {
        this.transactionRepository = transactionRepository;
        this.currentUserService = currentUserService;
    }

    /**
     * Get monthly dashboard aggregations for the current user's household.
     *
     * @param month the month in YYYY-MM format
     * @return dashboard response with aggregations
     * @throws IllegalArgumentException if month format is invalid
     */
    @Transactional(readOnly = true)
    public DashboardResponse getMonthlyDashboard(String month) {
        YearMonth yearMonth = parseMonth(month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        UUID householdId = currentUserService.getCurrentHouseholdId();

        // Get total income for the month
        Long totalIncomeCents = transactionRepository.sumAmountByHouseholdAndTypeAndDateRange(
                householdId, TransactionType.INCOME, startDate, endDate
        );

        // Get total expenses for the month
        Long totalExpenseCents = transactionRepository.sumAmountByHouseholdAndTypeAndDateRange(
                householdId, TransactionType.EXPENSE, startDate, endDate
        );

        // Calculate net balance
        Long netCents = totalIncomeCents - totalExpenseCents;

        // Get expenses breakdown by category
        List<Object[]> expenseData = transactionRepository.sumExpensesByCategory(
                householdId, TransactionType.EXPENSE, startDate, endDate
        );
        List<CategoryExpenseResponse> expensesByCategory = expenseData.stream()
                .map(row -> new CategoryExpenseResponse(
                        (UUID) row[0],
                        (String) row[1],
                        (Long) row[2]
                ))
                .toList();

        return new DashboardResponse(
                month,
                totalIncomeCents,
                totalExpenseCents,
                netCents,
                expensesByCategory
        );
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month, MONTH_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid month format. Expected YYYY-MM, got: " + month);
        }
    }
}
