package com.lifeos.entity;

import com.lifeos.entity.enums.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "finance_transactions")
@Getter
@Setter
public class FinanceTransaction extends BaseEntity {

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", length = 16, nullable = false)
    private TransactionType transactionType = TransactionType.EXPENSE;

    @Column(name = "amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", length = 8, nullable = false)
    private String currency = "USD";

    @Column(name = "category", length = 48, nullable = false)
    private String category;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Column(name = "is_recurring", nullable = false)
    private boolean recurring;

    @Column(name = "recurrence_rule", length = 120)
    private String recurrenceRule;

    @Column(name = "account", length = 80)
    private String account;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}