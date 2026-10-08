package com.lifeos.controller;

import com.lifeos.common.PageResponse;
import com.lifeos.dto.FinanceDtos;
import com.lifeos.entity.enums.TransactionType;
import com.lifeos.security.CurrentUser;
import com.lifeos.service.FinanceService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Transactions, budgets, savings goals and spending analysis. */
@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    private final FinanceService financeService;

    public FinanceController(FinanceService financeService) {
        this.financeService = financeService;
    }

    @GetMapping("/transactions")
    public PageResponse<FinanceDtos.TransactionResponse> transactions(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "25") Integer size) {
        return PageResponse.of(financeService.transactions(CurrentUser.id(), type, category, from, to,
                page == null ? 0 : page, size == null ? 25 : size));
    }

    @PostMapping("/transactions")
    public ResponseEntity<FinanceDtos.TransactionResponse> create(
            @Valid @RequestBody FinanceDtos.TransactionRequest request) {
        FinanceDtos.TransactionResponse created = financeService.create(CurrentUser.id(), request);
        return ResponseEntity.created(UriComponentsBuilder.fromPath("/api/finance/transactions/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PutMapping("/transactions/{id}")
    public FinanceDtos.TransactionResponse update(@PathVariable String id,
                                                  @Valid @RequestBody FinanceDtos.TransactionRequest request) {
        return financeService.update(CurrentUser.id(), id, request);
    }

    @DeleteMapping("/transactions/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable String id) {
        financeService.delete(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/budgets")
    public List<FinanceDtos.BudgetResponse> budgets() {
        return financeService.budgets(CurrentUser.id());
    }

    @PostMapping("/budgets")
    public ResponseEntity<FinanceDtos.BudgetResponse> createBudget(
            @Valid @RequestBody FinanceDtos.BudgetRequest request) {
        FinanceDtos.BudgetResponse created = financeService.createBudget(CurrentUser.id(), request);
        return ResponseEntity.created(UriComponentsBuilder.fromPath("/api/finance/budgets/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @DeleteMapping("/budgets/{id}")
    public ResponseEntity<Void> deleteBudget(@PathVariable String id) {
        financeService.deleteBudget(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/savings-goals")
    public List<FinanceDtos.SavingsGoalResponse> savingsGoals() {
        return financeService.savingsGoals(CurrentUser.id());
    }

    @PostMapping("/savings-goals")
    public ResponseEntity<FinanceDtos.SavingsGoalResponse> createSavingsGoal(
            @Valid @RequestBody FinanceDtos.SavingsGoalRequest request) {
        FinanceDtos.SavingsGoalResponse created = financeService.createSavingsGoal(CurrentUser.id(), request);
        return ResponseEntity.created(UriComponentsBuilder.fromPath("/api/finance/savings-goals/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PutMapping("/savings-goals/{id}")
    public FinanceDtos.SavingsGoalResponse updateSavingsGoal(
            @PathVariable String id, @Valid @RequestBody FinanceDtos.SavingsGoalRequest request) {
        return financeService.updateSavingsGoal(CurrentUser.id(), id, request);
    }

    @DeleteMapping("/savings-goals/{id}")
    public ResponseEntity<Void> deleteSavingsGoal(@PathVariable String id) {
        financeService.deleteSavingsGoal(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/overview")
    public FinanceDtos.FinanceOverview overview() {
        return financeService.overview(CurrentUser.id());
    }

    @GetMapping("/summary")
    public FinanceDtos.MonthlyFinanceSummary monthlySummary(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return financeService.monthlySummary(CurrentUser.id(), month);
    }

    @GetMapping("/trend")
    public List<FinanceDtos.MonthlyPoint> trend(@RequestParam(required = false, defaultValue = "12") Integer months) {
        return financeService.monthlyTrend(CurrentUser.id(), months == null ? 12 : months);
    }

    @GetMapping("/spending-patterns")
    public List<FinanceDtos.SpendingPattern> spendingPatterns() {
        return financeService.spendingPatterns(CurrentUser.id());
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return financeService.categories(CurrentUser.id());
    }
}
