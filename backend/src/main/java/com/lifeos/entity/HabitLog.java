package com.lifeos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "habit_logs")
@Getter
@Setter
public class HabitLog extends CreatedEntity {

    @Column(name = "habit_id", length = 36, nullable = false)
    private String habitId;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(name = "completed", nullable = false)
    private boolean completed = true;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "note", length = 500)
    private String note;
}