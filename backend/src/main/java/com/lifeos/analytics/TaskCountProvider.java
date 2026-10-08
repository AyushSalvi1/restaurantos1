package com.lifeos.analytics;

import com.lifeos.entity.Task;
import com.lifeos.entity.enums.TaskStatus;
import com.lifeos.repository.TaskRepository;
import com.lifeos.repository.TaskSpecifications;
import com.lifeos.util.DateSupport;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Counts tasks whose deadline passed while still open. Feeds the insight and prediction engines. */
@Component
public class TaskCountProvider {

    private final TaskRepository taskRepository;

    public TaskCountProvider(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public long countMissedInRange(String userId, LocalDate from, LocalDate to) {
        ZoneId zone = ZoneId.of("UTC");
        Instant start = DateSupport.startOfDay(from, zone);
        Instant end = DateSupport.endOfDay(to, zone);
        Specification<Task> spec = Specification.allOf(
                TaskSpecifications.ownedBy(userId),
                TaskSpecifications.withStatuses(List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS)),
                TaskSpecifications.withDeadlineRange(start, end),
                TaskSpecifications.overdueAt(end));
        return taskRepository.findAll(spec, Pageable.unpaged()).getTotalElements();
    }

    @Transactional(readOnly = true)
    public long countOpen(String userId) {
        return taskRepository.countByUserIdAndStatusAndDeletedAtIsNull(userId, TaskStatus.TODO)
                + taskRepository.countByUserIdAndStatusAndDeletedAtIsNull(userId, TaskStatus.IN_PROGRESS);
    }
}