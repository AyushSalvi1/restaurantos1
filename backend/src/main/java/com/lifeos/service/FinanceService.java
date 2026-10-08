package com.lifeos.service;

import com.lifeos.dto.FinanceDtos;
import com.lifeos.entity.Budget;
import com.lifeos.entity.FinanceTransaction;
import com.lifeos.entity.SavingsGoal;
import com.lifeos.entity.enums.BudgetPeriod;
import com.lifeos.entity.enums.TransactionType;
import com.lifeos.exception.AppException;
import com.lifeos.repository.BudgetRepository;
import com.lifeos.repository.FinanceTransactionRepository;
import com.lifeos.repository.SavingsGoalRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Personal finance: transactions, budgets, savings goals and monthly reports.
 *
 * <p>Spend analysis is descriptive — observed comparisons between periods — and never presented
 * as a guarantee about future money.</p>
 */
@Service
public class FinanceService {

    private static final String CURRENCY = "USD";

    private final FinanceTransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final SavingsGoalRepository savingsGoalRepository;
    private final UserZoneService userZoneService;

    public FinanceService(FinanceTransactionRepository transactionRepository,
                          BudgetRepository budgetRepository,
                          SavingsGoalRepository savingsGoalRepository,
                          UserZoneService userZoneService) {
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
        this.savingsGoalRepository = savingsGoalRepository;
        this.userZoneService = userZoneService;
    }

    @Transactional(readOnly = true)
    public Page<FinanceDtos.TransactionResponse> transactions(String userId, TransactionType type, String category,
                                                            LocalDate from, LocalDate to, int page, int size) {
        java.time.LocalDate start = from == null ? LocalDate.now(userZoneService.zoneOf(userId)).minusMonths(3) : from;
        java.time.LocalDate end = to == null ? LocalDate.now(userZoneService.zoneOf(userId)) : to;
        List<FinanceTransaction> rows = transactionRepository.findInRange(userId, start, end).stream()
                .filter(transaction -> type == null || transaction.getTransactionType() == type)
                .filter(transaction -> category == null || category.isBlank()
                        || category.equalsIgnoreCase(transaction.getCategory()))
                .sorted((a, b) -> b.getOccurredOn().compareTo(a.getOccurredOn()))
                .toList();
        int from_ = Math.max(0, page) * size;
        int to_ = Math.min(rows.size(), from_ + size);
        List<FinanceDtos.TransactionResponse> content = rows.subList(Math.min(from_, rows.size()), to_)
                .stream().map(this::toResponse).toList();
        return new org.springframework.data.domain.PageImpl<>(content,
                PageRequest.of(Math.max(0, page), size, Sort.by(Sort.Order.desc("occurredOn"))), rows.size());
    }

    @Transactional
    public FinanceDtos.TransactionResponse create(String userId, FinanceDtos.TransactionRequest request) {
        if (request.occurredOn().isAfter(LocalDate.now(userZoneService.zoneOf(userId)).plusMonths(1))) {
            throw AppException.badRequest("A transaction cannot be dated more than a month in the future");
        }
        FinanceTransaction transaction = new FinanceTransaction();
        transaction.setUserId(userId);
        transaction.setTransactionType(request.transactionType());
        transaction.setAmount(request.amount().setScale(2, RoundingMode.HALF_UP));
        transaction.setCategory(request.category().trim());
        transaction.setDescription(request.description());
        transaction.setOccurredOn(request.occurredOn());
        transaction.setRecurring(Boolean.TRUE.equals(request.recurring()));
        transaction.setRecurrenceRule(request.recurrenceRule());
        transaction.setAccount(request.account());
        transaction.setCurrency(request.currency() == null || request.currency().isBlank()
                ? CURRENCY : request.currency().toUpperCase(java.util.Locale.ROOT));
        transactionRepository.save(transaction);
        return toResponse(transaction);
    }

    @Transactional
    public FinanceDtos.TransactionResponse update(String userId, String id, FinanceDtos.TransactionRequest request) {
        FinanceTransaction transaction = requireOwned(userId, id);
        transaction.setTransactionType(request.transactionType());
        transaction.setAmount(request.amount().setScale(2, RoundingMode.HALF_UP));
        transaction.setCategory(request.category().trim());
        transaction.setDescription(request.description());
        transaction.setOccurredOn(request.occurredOn());
        transaction.setRecurring(Boolean.TRUE.equals(request.recurring()));
        transaction.setRecurrenceRule(request.recurrenceRule());
        transaction.setAccount(request.account());
        transactionRepository.save(transaction);
        return toResponse(transaction);
    }

    @Transactional
    public void delete(String userId, String id) {
        FinanceTransaction transaction = requireOwned(userId, id);
        transaction.setDeletedAt(Instant.now());
        transactionRepository.save(transaction);
    }

    // ---------------------------------------------------------------- budgets

    @Transactional(readOnly = true)
    public List<FinanceDtos.BudgetResponse> budgets(String userId) {
        LocalDate today = LocalDate.now(userZoneService.zoneOf(userId));
        return budgetRepository.findActiveOn(userId, today).stream().map(this::toBudgetResponse).toList();
    }

    @Transactional
    public FinanceDtos.BudgetResponse createBudget(String userId, FinanceDtos.BudgetRequest request) {
        if (!request.endDate().isAfter(request.startDate())) {
            throw AppException.badRequest("The budget end date must be after its start date");
        }
        Budget budget = new Budget();
        budget.setUserId(userId);
        apply(budget, request);
        budgetRepository.save(budget);
        return toBudgetResponse(budget);
    }

    @Transactional
    public void deleteBudget(String userId, String id) {
        Budget budget = budgetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> AppException.notFound("Budget not found"));
        budgetRepository.delete(budget);
    }

    // ------------------------------------------------------------ savings goals

    @Transactional(readOnly = true)
    public List<FinanceDtos.SavingsGoalResponse> savingsGoals(String userId) {
        return savingsGoalRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toSavingsResponse).toList();
    }

    @Transactional
    public FinanceDtos.SavingsGoalResponse createSavingsGoal(String userId, FinanceDtos.SavingsGoalRequest request) {
        SavingsGoal goal = new SavingsGoal();
        goal.setUserId(userId);
        goal.setName(request.name());
        goal.setTargetAmount(request.targetAmount());
        goal.setSavedAmount(request.savedAmount() == null ? BigDecimal.ZERO : request.savedAmount());
        goal.setTargetDate(request.targetDate());
        goal.setNotes(request.notes());
        savingsGoalRepository.save(goal);
        return toSavingsResponse(goal);
    }

    @Transactional
    public FinanceDtos.SavingsGoalResponse updateSavingsGoal(String userId, String id, FinanceDtos.SavingsGoalRequest request) {
        SavingsGoal goal = savingsGoalRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> AppException.notFound("Savings goal not found"));
        goal.setName(request.name());
        goal.setTargetAmount(request.targetAmount());
        if (request.savedAmount() != null) {
            goal.setSavedAmount(request.savedAmount());
        }
        goal.setTargetDate(request.targetDate());
        goal.setNotes(request.notes());
        savingsGoalRepository.save(goal);
        return toSavingsResponse(goal);
    }

    @Transactional
    public void deleteSavingsGoal(String userId, String id) {
        SavingsGoal goal = savingsGoalRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> AppException.notFound("Savings goal not found"));
        savingsGoalRepository.delete(goal);
    }

    // -------------------------------------------------------------- reporting

    @Transactional(readOnly = true)
    public FinanceDtos.FinanceOverview overview(String userId) {
        java.time.ZoneId zone = userZoneService.zoneOf(userId);
        LocalDate today = LocalDate.now(zone);
        YearMonth month = YearMonth.from(today);

        BigDecimal income = sum(userId, TransactionType.INCOME, month);
        BigDecimal expenses = sum(userId, TransactionType.EXPENSE, month);
        BigDecimal savings = income.subtract(expenses);

        List<FinanceDtos.BudgetResponse> budgets = budgets(userId);
        BigDecimal overallBudget = budgets.stream()
                .filter(budget -> budget.category() == null)
                .map(FinanceDtos.BudgetResponse::amount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
        BigDecimal remaining = overallBudget.compareTo(BigDecimal.ZERO) > 0
                ? overallBudget.subtract(expenses)
                : BigDecimal.ZERO;

        return new FinanceDtos.FinanceOverview(
                income,
                expenses,
                savings,
                remaining,
                budgets,
                categoryTotals(userId, TransactionType.EXPENSE, month),
                monthlyTrend(userId, 12),
                savingsGoals(userId),
                CURRENCY);
    }

    /**
     * Totals for one calendar month. The month is optional in the API, so it defaults to the current month in
     * the user's own timezone rather than failing on a null.
     */
    @Transactional(readOnly = true)
    public FinanceDtos.MonthlyFinanceSummary monthlySummary(String userId, YearMonth month) {
        YearMonth effective = month == null ? YearMonth.now(userZoneService.zoneOf(userId)) : month;
        BigDecimal income = sum(userId, TransactionType.INCOME, effective);
        BigDecimal expenses = sum(userId, TransactionType.EXPENSE, effective);
        List<FinanceDtos.CategoryTotal> expenseCategories =
                categoryTotals(userId, TransactionType.EXPENSE, effective);

        BigDecimal budgetAmount = budgetRepository.findActiveOn(userId, effective.atDay(1)).stream()
                .filter(active -> active.getCategory() == null)
                .map(Budget::getAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);

        return new FinanceDtos.MonthlyFinanceSummary(
                effective.atDay(1),
                income,
                expenses,
                income.subtract(expenses),
                budgetAmount.compareTo(BigDecimal.ZERO) > 0 ? budgetAmount.subtract(expenses) : BigDecimal.ZERO,
                expenseCategories,
                categoryTotals(userId, TransactionType.INCOME, effective));
    }

    @Transactional(readOnly = true)
    public List<FinanceDtos.MonthlyPoint> monthlyTrend(String userId, int months) {
        java.time.ZoneId zone = userZoneService.zoneOf(userId);
        YearMonth current = YearMonth.from(LocalDate.now(zone));
        List<FinanceDtos.MonthlyPoint> points = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            YearMonth month = current.minusMonths(i);
            BigDecimal income = sum(userId, TransactionType.INCOME, month);
            BigDecimal expenses = sum(userId, TransactionType.EXPENSE, month);
            points.add(new FinanceDtos.MonthlyPoint(month.atDay(1), income, expenses, income.subtract(expenses)));
        }
        return points;
    }

    /** Descriptive month-over-month comparison. Labelled as observed change, never as advice. */
    @Transactional(readOnly = true)
    public List<FinanceDtos.SpendingPattern> spendingPatterns(String userId) {
        java.time.ZoneId zone = userZoneService.zoneOf(userId);
        YearMonth current = YearMonth.from(LocalDate.now(zone));
        YearMonth previous = current.minusMonths(1);

        Map<String, BigDecimal> currentTotals = totalsByCategory(userId, current);
        Map<String, BigDecimal> previousTotals = totalsByCategory(userId, previous);

        java.util.Set<String> categories = new java.util.TreeSet<>();
        categories.addAll(currentTotals.keySet());
        categories.addAll(previousTotals.keySet());

        List<FinanceDtos.SpendingPattern> patterns = new ArrayList<>();
        for (String category : categories) {
            BigDecimal now = currentTotals.getOrDefault(category, BigDecimal.ZERO);
            BigDecimal before = previousTotals.getOrDefault(category, BigDecimal.ZERO);
            double change = before.compareTo(BigDecimal.ZERO) == 0
                    ? (now.compareTo(BigDecimal.ZERO) == 0 ? 0 : 100)
                    : now.subtract(before).multiply(BigDecimal.valueOf(100))
                    .divide(before, 1, RoundingMode.HALF_UP).doubleValue();
            patterns.add(new FinanceDtos.SpendingPattern(category, now, before, change,
                    change > 0 ? "INCREASED" : change < 0 ? "DECREASED" : "UNCHANGED"));
        }
        patterns.sort((a, b) -> Double.compare(Math.abs(b.changePercent()), Math.abs(a.changePercent())));
        return patterns;
    }

    @Transactional(readOnly = true)
    public List<String> categories(String userId) {
        return transactionRepository.findCategories(userId);
    }

    // ---------------------------------------------------------------- helpers

    @Transactional(readOnly = true)
    public FinanceTransaction requireOwned(String userId, String id) {
        return transactionRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> AppException.notFound("Transaction not found"));
    }

    private void apply(Budget budget, FinanceDtos.BudgetRequest request) {
        budget.setCategory(request.category() == null || request.category().isBlank() ? null : request.category());
        budget.setPeriod(request.period() == null ? BudgetPeriod.MONTHLY : request.period());
        budget.setAmount(request.amount());
        budget.setStartDate(request.startDate());
        budget.setEndDate(request.endDate());
    }

    private FinanceDtos.BudgetResponse toBudgetResponse(Budget budget) {
        java.time.ZoneId zone = userZoneService.zoneOf(userIdOf(budget));
        YearMonth month = YearMonth.from(LocalDate.now(zone));
        BigDecimal spent = transactionRepository.sumAmountByCategory(budget.getUserId(), TransactionType.EXPENSE,
                budget.getCategory(), month.atDay(1), month.atEndOfMonth());
        double utilisation = budget.getAmount().compareTo(BigDecimal.ZERO) == 0 ? 0
                : Math.round(spent.multiply(BigDecimal.valueOf(100)).divide(budget.getAmount(), 1, RoundingMode.HALF_UP)
                .doubleValue());
        return new FinanceDtos.BudgetResponse(budget.getId(), budget.getCategory(), budget.getPeriod(),
                budget.getAmount(), budget.getStartDate(), budget.getEndDate(), spent, utilisation, utilisation >= 100);
    }

    private String userIdOf(Budget budget) {
        return budget.getUserId();
    }

    private BigDecimal sum(String userId, TransactionType type, YearMonth month) {
        return nullSafe(transactionRepository.sumAmount(userId, type, month.atDay(1), month.atEndOfMonth()));
    }

    private List<FinanceDtos.CategoryTotal> categoryTotals(String userId, TransactionType type, YearMonth month) {
        List<FinanceDtos.CategoryTotal> totals = new ArrayList<>();
        List<FinanceTransaction> rows = transactionRepository.findInRange(userId, month.atDay(1), month.atEndOfMonth());
        Map<String, List<FinanceTransaction>> grouped = rows.stream()
                .filter(transaction -> transaction.getTransactionType() == type)
                .collect(java.util.stream.Collectors.groupingBy(FinanceTransaction::getCategory));
        BigDecimal grand = grouped.values().stream()
                .map(list -> list.stream().map(FinanceTransaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        grouped.forEach((category, list) -> {
            BigDecimal total = list.stream().map(FinanceTransaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            double share = grand.compareTo(BigDecimal.ZERO) == 0 ? 0
                    : total.multiply(BigDecimal.valueOf(100)).divide(grand, 1, RoundingMode.HALF_UP).doubleValue();
            totals.add(new FinanceDtos.CategoryTotal(category, total, share, list.size()));
        });
        totals.sort((a, b) -> b.total().compareTo(a.total()));
        return totals;
    }

    private Map<String, BigDecimal> totalsByCategory(String userId, YearMonth month) {
        Map<String, BigDecimal> result = new java.util.HashMap<>();
        transactionRepository.findInRange(userId, month.atDay(1), month.atEndOfMonth()).stream()
                .filter(transaction -> transaction.getTransactionType() == TransactionType.EXPENSE)
                .forEach(transaction -> result.merge(transaction.getCategory(), transaction.getAmount(), BigDecimal::add));
        return result;
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private FinanceDtos.TransactionResponse toResponse(FinanceTransaction transaction) {
        return new FinanceDtos.TransactionResponse(
                transaction.getId(),
                transaction.getTransactionType(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getCategory(),
                transaction.getDescription(),
                transaction.getOccurredOn(),
                transaction.isRecurring(),
                transaction.getRecurrenceRule(),
                transaction.getAccount(),
                transaction.getCreatedAt());
    }

    private FinanceDtos.SavingsGoalResponse toSavingsResponse(SavingsGoal goal) {
        double progress = goal.getTargetAmount().compareTo(BigDecimal.ZERO) == 0 ? 0
                : Math.min(100, goal.getSavedAmount().multiply(BigDecimal.valueOf(100))
                .divide(goal.getTargetAmount(), 1, RoundingMode.HALF_UP).doubleValue());
        return new FinanceDtos.SavingsGoalResponse(goal.getId(), goal.getName(), goal.getTargetAmount(),
                goal.getSavedAmount(), progress, goal.getTargetDate(), goal.getNotes());
    }
}