package com.lifeos.entity;

import com.lifeos.entity.enums.InsightSeverity;
import com.lifeos.entity.enums.InsightType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "insights")
@Getter
@Setter
public class Insight extends CreatedEntity {

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "insight_type", length = 32, nullable = false)
    private InsightType insightType;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "body", length = 1000, nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 16, nullable = false)
    private InsightSeverity severity = InsightSeverity.INFO;

    @Column(name = "confidence", nullable = false)
    private int confidence;

    /** JSON array of the concrete measurements that produced this insight. */
    @Column(name = "factors", columnDefinition = "TEXT")
    private String factors;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "metric_date")
    private LocalDate metricDate;

    @Column(name = "source", length = 16, nullable = false)
    private String source = "RULE";

    @Column(name = "dedupe_key", length = 190)
    private String dedupeKey;

    @Column(name = "dismissed", nullable = false)
    private boolean dismissed;
}