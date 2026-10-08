package com.lifeos.entity;

import com.lifeos.entity.enums.PredictionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "predictions")
@Getter
@Setter
public class Prediction extends CreatedEntity {

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "prediction_type", length = 32, nullable = false)
    private PredictionType predictionType;

    @Column(name = "subject_type", length = 32, nullable = false)
    private String subjectType;

    @Column(name = "subject_id", length = 36, nullable = false)
    private String subjectId;

    @Column(name = "label", length = 200, nullable = false)
    private String label;

    @Column(name = "probability", nullable = false)
    private int probability;

    @Column(name = "confidence", nullable = false)
    private int confidence;

    /** JSON array of the inputs that produced the probability. */
    @Column(name = "factors", columnDefinition = "TEXT")
    private String factors;

    @Column(name = "horizon_days", nullable = false)
    private int horizonDays;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}