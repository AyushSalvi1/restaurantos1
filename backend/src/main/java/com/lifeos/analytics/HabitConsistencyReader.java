package com.lifeos.analytics;

import com.lifeos.dto.AnalyticsDtos;
import com.lifeos.entity.Habit;
import com.lifeos.repository.HabitLogRepository;
import com.lifeos.repository.HabitRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** Habit dimension: completion rate against each active habit's own target days per week. */
@Component
public class HabitConsistencyReader {

    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;

    public HabitConsistencyReader(HabitRepository habitRepository, HabitLogRepository habitLogRepository) {
        this.habitRepository = habitRepository;
        this.habitLogRepository = habitLogRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.BalanceDimension dimension(String userId, LocalDate from, LocalDate to) {
        List<Habit> habits = habitRepository.findByUserIdAndArchivedFalse(userId);
        if (habits.isEmpty()) {
            return BalanceScoreService.insufficient("habits", "Habits",
                    "No active habits, so consistency cannot be scored.");
        }
        int days = Math.max(1, (int) java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1);
        int weeks = Math.max(1, days / 7);
        double total = 0;
        int scored = 0;
        StringBuilder inputs = new StringBuilder();
        for (Habit habit : habits) {
            long done = habitLogRepository
                    .countByUserIdAndHabitIdAndCompletedTrueAndLogDateBetween(userId, habit.getId(), from, to);
            int target = habit.getTargetDays() == null || habit.getTargetDays().isBlank()
                    ? 7
                    : parseTargetDays(habit.getTargetDays());
            double rate = Math.min(100, done * 100.0 / (target * (double) weeks));
            total += rate;
            scored++;
            if (inputs.length() < 800) {
                inputs.append(habit.getName()).append(": ").append(done).append(" of ")
                        .append(target * weeks).append(" scheduled").append(System.lineSeparator());
            }
        }
        double mean = total / scored;
        return new AnalyticsDtos.BalanceDimension("habits", "Habits",
                (int) Math.round(mean), 100,
                "For each active habit, check-ins against its own target days per week, averaged across habits.",
                inputs.toString().lines().toList(), true);
    }

    private int parseTargetDays(String targetDays) {
        try {
            int parsed = Integer.parseInt(targetDays.strip());
            return Math.max(1, Math.min(7, parsed));
        } catch (NumberFormatException ex) {
            return 7;
        }
    }
}
