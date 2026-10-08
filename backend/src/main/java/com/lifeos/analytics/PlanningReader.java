package com.lifeos.analytics;

import com.lifeos.dto.AnalyticsDtos;
import com.lifeos.entity.ProductivityMetric;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Planning dimension: how much of each day's committed work actually happened.
 * Reads only the already-computed daily metrics, so it adds no extra queries.
 */
@Component
public class PlanningReader {

    public AnalyticsDtos.BalanceDimension dimension(List<ProductivityMetric> metrics) {
        int measured = 0;
        int plannedMinutes = 0;
        int completedMinutes = 0;
        for (ProductivityMetric metric : metrics) {
            if (metric.getPlannedMinutes() <= 0 && metric.getTasksPlanned() <= 0) {
                continue;
            }
            measured++;
            plannedMinutes += metric.getPlannedMinutes();
            completedMinutes += metric.getCompletedMinutes();
        }
        if (measured == 0) {
            return BalanceScoreService.insufficient("planning", "Planning",
                    "No day in the last 30 had anything planned, so planning cannot be scored. "
                            + "Scheduling tasks or events on a day makes this measurable.");
        }
        double ratio = plannedMinutes == 0 ? 0 : completedMinutes * 100.0 / plannedMinutes;
        double score = Math.min(100, ratio);
        return new AnalyticsDtos.BalanceDimension("planning", "Planning",
                (int) Math.round(score), 100,
                "Completed minutes as a share of planned minutes across the days that had a plan.",
                List.of("Days with a plan: " + measured,
                        "Planned minutes: " + plannedMinutes,
                        "Completed minutes: " + completedMinutes,
                        "Completion of plan: " + BalanceScoreService.round(ratio) + "%"),
                true);
    }
}