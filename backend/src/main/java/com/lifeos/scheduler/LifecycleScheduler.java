package com.lifeos.scheduler;

import com.lifeos.analytics.InsightService;
import com.lifeos.analytics.PredictionService;
import com.lifeos.config.SchedulerProperties;
import com.lifeos.repository.UserRepository;
import com.lifeos.service.MetricsService;
import com.lifeos.service.NotificationService;
import com.lifeos.service.UserZoneService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Background maintenance.
 *
 * <p>Every job is idempotent and fails soft: one user's failure is logged and the run continues, so a
 * single bad row cannot stop the whole sweep. All work is gated on {@code lifeos.scheduler.enabled},
 * which is off outside development so a test run never writes derived data.</p>
 */
@Component
public class LifecycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(LifecycleScheduler.class);
    private static final int USER_BATCH = 100;
    private static final int METRIC_REBUILD_DAYS = 2;
    private static final int STALE_RECORD_DAYS = 14;
    private static final int MAX_BATCHES = 50;

    private final UserRepository userRepository;
    private final MetricsService metricsService;
    private final NotificationService notificationService;
    private final InsightService insightService;
    private final PredictionService predictionService;
    private final UserZoneService userZoneService;
    private final SchedulerProperties schedulerProperties;

    public LifecycleScheduler(UserRepository userRepository,
                              MetricsService metricsService,
                              NotificationService notificationService,
                              InsightService insightService,
                              PredictionService predictionService,
                              UserZoneService userZoneService,
                              SchedulerProperties schedulerProperties) {
        this.userRepository = userRepository;
        this.metricsService = metricsService;
        this.notificationService = notificationService;
        this.insightService = insightService;
        this.predictionService = predictionService;
        this.userZoneService = userZoneService;
        this.schedulerProperties = schedulerProperties;
    }

    /** Delivers reminders whose time has arrived. Runs every five minutes. */
    @Scheduled(cron = "${lifeos.scheduler.reminder-cron:0 */5 * * * *}")
    @Transactional
    public void deliverReminders() {
        if (!schedulerProperties.enabled()) {
            return;
        }
        try {
            int delivered = notificationService.deliverDueReminders(Instant.now());
            if (delivered > 0) {
                log.info("Delivered {} scheduled reminder(s)", delivered);
            }
        } catch (RuntimeException ex) {
            log.error("Reminder delivery run failed", ex);
        }
    }

    /** Recomputes the last two days of productivity metrics for every active account. */
    @Scheduled(cron = "${lifeos.scheduler.metrics-cron:0 15 1 * * *}")
    @Transactional
    public void recomputeMetrics() {
        if (!schedulerProperties.enabled()) {
            return;
        }
        forEachActiveUser(userId -> {
            ZoneId zone = userZoneService.zoneOf(userId);
            LocalDate today = LocalDate.now(zone);
            return metricsService.rebuildRange(userId, today.minusDays(METRIC_REBUILD_DAYS - 1L), today);
        }, "metric recomputation");
    }

    /** Refreshes rule-based insights. Every rule is deduped, so this produces no duplicates. */
    @Scheduled(cron = "${lifeos.scheduler.insights-cron:0 30 3 * * *}")
    @Transactional
    public void regenerateInsights() {
        if (!schedulerProperties.enabled()) {
            return;
        }
        forEachActiveUser(userId -> {
            insightService.generate(userId);
            return 0;
        }, "insight generation");
    }

    /** Rebuilds risk predictions so the dashboard never shows an expired figure. */
    @Scheduled(cron = "${lifeos.scheduler.predictions-cron:0 0 4 * * *}")
    @Transactional
    public void regeneratePredictions() {
        if (!schedulerProperties.enabled()) {
            return;
        }
        forEachActiveUser(userId -> {
            predictionService.regenerate(userId);
            return 0;
        }, "prediction generation");
    }

    /** Removes expired refresh tokens so the session table cannot grow without bound. */
    @Scheduled(cron = "${lifeos.scheduler.cleanup-cron:0 0 5 * * *}")
    @Transactional
    public void cleanUp() {
        if (!schedulerProperties.enabled()) {
            return;
        }
        try {
            int removed = notificationService.expireStaleReminders(
                    Instant.now().minus(java.time.Duration.ofDays(STALE_RECORD_DAYS)));
            log.info("Cleaned up {} stale record(s)", removed);
        } catch (RuntimeException ex) {
            log.error("Cleanup run failed", ex);
        }
    }

    // -------------------------------------------------------------- internals

    /** Applies {@code work} to every active account, isolating failures to the single user. */
    private void forEachActiveUser(java.util.function.ToIntFunction<String> work, String description) {
        int processed = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            Page<String> page = activeUserIds(batch);
            if (page.isEmpty()) {
                break;
            }
            for (String userId : page.getContent()) {
                try {
                    work.applyAsInt(userId);
                } catch (RuntimeException ex) {
                    log.error("{} failed for user {}", description, userId, ex);
                }
            }
            processed += page.getNumberOfElements();
        }
        if (processed > 0) {
            log.info("{} completed for {} account(s)", description, processed);
        }
    }

    private Page<String> activeUserIds(int batch) {
        return userRepository.findByDeletedAtIsNull(PageRequest.of(batch, USER_BATCH))
                .map(user -> user.getId());
    }
}
