package com.lifeos.analytics;

import com.lifeos.dto.AnalyticsDtos;
import com.lifeos.entity.Budget;
import com.lifeos.entity.enums.TransactionType;
import com.lifeos.repository.BudgetRepository;
import com.lifeos.repository.FinanceTransactionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Finance dimension: how close spending sits to the budgets the user actually set.
 * Income is deliberately excluded — the score measures spending discipline, not income.
 */
@Component
public class FinanceHealthReader {

    private final BudgetRepository budgetRepository;
    private final FinanceTransactionRepository transactionRepository;

    public FinanceHealthReader(BudgetRepository budgetRepository,
                               FinanceTransactionRepository transactionRepository) {
        this.budgetRepository = budgetRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.BalanceDimension dimension(String userId) {
        LocalDate today = LocalDate.now();
        List<Budget> budgets = budgetRepository.findActiveOn(userId, today);
        if (budgets.isEmpty()) {
            return BalanceScoreService.insufficient("finance", "Finance",
                    "No budgets are active today, so spending against plan cannot be scored.");
        }
        List<String> inputs = new ArrayList<>();
        double total = 0;
        int scored = 0;
        int overCount = 0;
        for (Budget budget : budgets) {
            BigDecimal limit = budget.getAmount() == null ? BigDecimal.ZERO : budget.getAmount();
            if (limit.signum() <= 0) {
                continue;
            }
            BigDecimal spent = transactionRepository.sumAmountByCategory(
                    userId, TransactionType.EXPENSE, budget.getCategory(),
                    budget.getStartDate(), budget.getEndDate() == null ? today : budget.getEndDate());
            if (spent == null) {
                spent = BigDecimal.ZERO;
            }
            double usage = spent.doubleValue() / limit.doubleValue() * 100.0;
            double score = usage <= 100 ? 100.0 : Math.max(0, 100.0 - (usage - 100.0) * 0.5);
            if (usage > 100) {
                overCount++;
            }
            total += score;
            scored++;
            inputs.add(String.format("%s: %s of %s (%.0f%% of budget)",
                    budget.getCategory(), spent.setScale(2, RoundingMode.HALF_UP),
                    limit.setScale(2, RoundingMode.HALF_UP), usage));
        }
        if (scored == 0) {
            return BalanceScoreService.insufficient("finance", "Finance",
                    "Active budgets have no limit set, so spending against plan cannot be scored.");
        }
        double mean = total / scored;
        inputs.add("Budgets over limit: " + overCount + " of " + scored);
        return new AnalyticsDtos.BalanceDimension("finance", "Finance",
                (int) Math.round(mean), 100,
                "For each active budget, spending against limit. At or below limit scores 100; each "
                        + "1% over removes half a point.",
                inputs, true);
    }
}