package com.lifeos.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeos.dto.AnalyticsDtos;
import com.lifeos.entity.ProductivityMetric;
import com.lifeos.entity.UserPreference;
import com.lifeos.repository.ProductivityMetricRepository;
import com.lifeos.service.UserZoneService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transparent Life Balance Score across six dimensions.
 *
 * <p>Every dimension is a ratio of measured progress against a stated target, and both the inputs
 * and the formula are returned to the client so the number is fully auditable. Dimensions without
 * data are marked {@code sufficientData=false} and excluded from the average rather than being
 * counted as zero — otherwise a new user would look worse than an inconsistent one.</p>
 */
@Service
public class BalanceScoreService {

    private static final Logger log = LoggerFactory.getLogger(BalanceScoreService.class);
    private static final String DISCLAIMER = "This score summarises recorded activity only. It is not a measure of a "
            + "person's worth, wellbeing or quality of life.";

    private static final List<String> DEFAULT_DIMENSIONS = List.of(
            "productivity", "learning", "goals", "habits", "finance", "planning");

    private final ProductivityMetricRepository metricRepository;
    private final GoalProgressReader goalProgressReader;
    private final HabitConsistencyReader habitConsistencyReader;
    private final FinanceHealthReader financeHealthReader;
    private final PlanningReader planningReader;
    private final LearningProgressReader learningProgressReader;
    private final com.lifeos.repository.UserPreferenceRepository preferenceRepository;
    private final ObjectMapper objectMapper;
    private final UserZoneService userZoneService;

    public BalanceScoreService(ProductivityMetricRepository metricRepository,
                               GoalProgressReader goalProgressReader,
                               HabitConsistencyReader habitConsistencyReader,
                               FinanceHealthReader financeHealthReader,
                               PlanningReader planningReader,
                               LearningProgressReader learningProgressReader,
                               com.lifeos.repository.UserPreferenceRepository preferenceRepository,
                               ObjectMapper objectMapper,
                               UserZoneService userZoneService) {
        this.metricRepository = metricRepository;
        this.goalProgressReader = goalProgressReader;
        this.habitConsistencyReader = habitConsistencyReader;
        this.financeHealthReader = financeHealthReader;
        this.planningReader = planningReader;
        this.learningProgressReader = learningProgressReader;
        this.preferenceRepository = preferenceRepository;
        this.objectMapper = objectMapper;
        this.userZoneService = userZoneService;
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.BalanceScore compute(String userId) {
        ZoneId zone = userZoneService.zoneOf(userId);
        LocalDate today = LocalDate.now(zone);
        LocalDate from = today.minusDays(29);
        List<ProductivityMetric> metrics = metricRepository
                .findByUserIdAndMetricDateBetweenOrderByMetricDateAsc(userId, from, today);

        Map<String, Integer> weights = resolveWeights(userId);

        List<AnalyticsDtos.BalanceDimension> dimensions = new ArrayList<>();
        dimensions.add(productivity(metrics));
        dimensions.add(learningProgressReader.dimension(userId));
        dimensions.add(goalProgressReader.dimension(userId));
        dimensions.add(habitConsistencyReader.dimension(userId, from, today));
        dimensions.add(financeHealthReader.dimension(userId));
        dimensions.add(planningReader.dimension(metrics));

        List<AnalyticsDtos.BalanceDimension> scored = new ArrayList<>();
        double weightedTotal = 0;
        double weightTotal = 0;
        for (AnalyticsDtos.BalanceDimension dimension : dimensions) {
            if (!dimension.sufficientData()) {
                scored.add(dimension);
                continue;
            }
            double weight = weights.getOrDefault(dimension.key(), 100);
            weightedTotal += dimension.score() * weight;
            weightTotal += weight;
        }

        int overall = weightTotal == 0 ? 0 : (int) Math.round(weightedTotal / weightTotal);
        boolean sufficient = weightTotal > 0;

        return new AnalyticsDtos.BalanceScore(
                overall,
                grade(overall),
                scored,
                "Each dimension is scored 0-100 from measured activity. The overall score is the "
                        + "weighted mean of the dimensions that have data, using the weights you set in Settings.",
                DISCLAIMER,
                sufficient);
    }

    @Transactional
    public AnalyticsDtos.BalanceScore updateWeights(String userId, Map<String, Integer> weights) {
        Map<String, Integer> sanitised = new LinkedHashMap<>();
        weights.forEach((key, value) -> {
            if (value != null) {
                sanitised.put(key, Math.max(0, Math.min(100, value)));
            }
        });
        String serialised;
        try {
            serialised = objectMapper.writeValueAsString(sanitised);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Weights could not be stored", ex);
        }
        // An account that has never been onboarded has no preference row yet. Creating it here is what makes the
// weights stick; without this the response would report a freshly computed score and the user's choice
// would be discarded without a word.
        UserPreference preference = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserPreference created = new UserPreference();
                    created.setUserId(userId);
                    return created;
                });
        preference.setLifeBalanceWeights(serialised);
        preferenceRepository.save(preference);
        return compute(userId);
    }

    private Map<String, Integer> resolveWeights(String userId) {
        Map<String, Integer> defaults = new LinkedHashMap<>();
        DEFAULT_DIMENSIONS.forEach(key -> defaults.put(key, 100));
        return preferenceRepository.findByUserId(userId)
                .map(preference -> {
                    if (preference.getLifeBalanceWeights() == null || preference.getLifeBalanceWeights().isBlank()) {
                        return defaults;
                    }
                    try {
                        Map<String, Integer> stored = objectMapper.readValue(preference.getLifeBalanceWeights(),
                                new com.fasterxml.jackson.core.type.TypeReference<>() {
                                });
                        stored.forEach((key, value) -> {
                            if (value != null) {
                                defaults.put(key, Math.max(0, Math.min(100, value)));
                            }
                        });
                    } catch (Exception ex) {
                        log.warn("Ignoring unreadable life balance weights: {}", ex.getMessage());
                    }
                    return defaults;
                })
                .orElse(defaults);
    }

    private AnalyticsDtos.BalanceDimension productivity(List<ProductivityMetric> metrics) {
        if (metrics.isEmpty()) {
            return insufficient("productivity", "Productivity",
                    "No daily productivity metrics recorded in the last 30 days.");
        }
        double total = metrics.stream().mapToInt(ProductivityMetric::getProductivityScore).average().orElse(0);
        long activeDays = metrics.stream().filter(metric -> metric.getTasksCompleted() > 0
                || metric.getFocusMinutes() > 0 || metric.getHabitsCompleted() > 0).count();
        return new AnalyticsDtos.BalanceDimension("productivity", "Productivity",
                (int) Math.round(total), 100,
                "Mean of the daily productivity score, which blends task completion, focus time, "
                        + "habit check-ins and study time.",
                List.of("Average daily score: " + round(total),
                        "Days with recorded activity: " + activeDays + "/" + metrics.size(),
                        "Metrics captured: " + metrics.size()),
                true);
    }

    static AnalyticsDtos.BalanceDimension insufficient(String key, String label, String explanation) {
        return new AnalyticsDtos.BalanceDimension(key, label, 0, 0, explanation, List.of(), false);
    }

    static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String grade(int score) {
        if (score >= 85) {
            return "Strong";
        }
        if (score >= 70) {
            return "Solid";
        }
        if (score >= 50) {
            return "Developing";
        }
        if (score > 0) {
            return "Early";
        }
        return "Not enough data";
    }
}