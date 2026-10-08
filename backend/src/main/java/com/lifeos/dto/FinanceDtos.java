package com.lifeos.dto;

import com.lifeos.entity.enums.BudgetPeriod;
import com.lifeos.entity.enums.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Finance payloads: transactions, budgets and savings goals. */
public final class FinanceDtos {

    private FinanceDtos() {
    }

    public record TransactionRequest(
            @NotNull TransactionType transactionType,
            @NotNull @DecimalMin(value = "0.01", message = "must be greater than zero") BigDecimal amount,
            @NotBlank @Size(max = 48) String category,
            @Size(max = 500) String description,
            @NotNull LocalDate occurredOn,
            Boolean recurring,
            @Size(max = 120) String recurrenceRule,
            @Size(max = 80) String account,
            @Size(max = 8) String currency
    ) {
    }

    public record TransactionResponse(
            String id,
            TransactionType transactionType,
            BigDecimal amount,
            String currency,
            String category,
            String description,
            LocalDate occurredOn,
            boolean recurring,
            String recurrenceRule,
            String account,
            Instant createdAt
    ) {
    }

    public record BudgetRequest(
            @Size(max = 48) String category,
            @NotNull BudgetPeriod period,
            @NotNull @DecimalMin(value = "0.01", message = "must be greater than zero") BigDecimal amount,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate
    ) {
    }

    public record BudgetResponse(
            String id,
            String category,
            BudgetPeriod period,
            BigDecimal amount,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal spent,
            double utilisationPercent,
            boolean exceeded
    ) {
    }

    public record SavingsGoalRequest(
            @NotBlank @Size(max = 160) String name,
            @NotNull @DecimalMin(value = "0.01", message = "must be greater than zero") BigDecimal targetAmount,
            @DecimalMin("0.0") BigDecimal savedAmount,
            LocalDate targetDate,
            @Size(max = 20000) String notes
    ) {
    }

    public record SavingsGoalResponse(
            String id,
            String name,
            BigDecimal targetAmount,
            BigDecimal savedAmount,
            double progressPercent,
            LocalDate targetDate,
            String notes
    ) {
    }

    public record CategoryTotal(String category, BigDecimal total, double sharePercent, long transactionCount) {
    }

    public record MonthlyFinanceSummary(
            LocalDate month,
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal savings,
            BigDecimal remainingBudget,
            List<CategoryTotal> expenseByCategory,
            List<CategoryTotal> incomeByCategory
    ) {
    }

    public record FinanceOverview(
            BigDecimal incomeThisMonth,
            BigDecimal expenseThisMonth,
            BigDecimal savingsThisMonth,
            BigDecimal remainingBudget,
            List<BudgetResponse> budgets,
            List<CategoryTotal> expenseByCategory,
            List<MonthlyPoint> monthlyTrend,
            List<SavingsGoalResponse> savingsGoals,
            String currency
    ) {
    }

    public record MonthlyPoint(LocalDate month, BigDecimal income, BigDecimal expenses, BigDecimal savings) {
    }

    public record SpendingPattern(
            String category,
            BigDecimal currentPeriod,
            BigDecimal previousPeriod,
            double changePercent,
            String direction
    ) {
    }
}