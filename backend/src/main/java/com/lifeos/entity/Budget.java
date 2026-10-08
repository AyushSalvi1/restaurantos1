package com.lifeos.entity;

import com.lifeos.entity.enums.BudgetPeriod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "budgets")
@Getter
@Setter
public class Budget extends BaseEntity {

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    /** {@code null} means an overall budget that is not tied to a single category. */
    @Column(name = "category", length = 48)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "period", length = 16, nullable = false)
    private BudgetPeriod period = BudgetPeriod.MONTHLY;

    @Column(name = "amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
}