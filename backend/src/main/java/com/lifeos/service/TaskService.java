package com.lifeos.service;

import com.lifeos.dto.TaskDtos;
import com.lifeos.entity.Task;
import com.lifeos.entity.TaskDependency;
import com.lifeos.entity.enums.GraphNodeType;
import com.lifeos.entity.enums.GraphRelation;
import com.lifeos.entity.enums.TaskStatus;
import com.lifeos.exception.AppException;
import com.lifeos.repository.GoalMilestoneRepository;
import com.lifeos.repository.GoalRepository;
import com.lifeos.repository.ProjectRepository;
import com.lifeos.repository.TaskDependencyRepository;
import com.lifeos.repository.TaskRepository;
import com.lifeos.repository.TaskSpecifications;
import com.lifeos.util.Csv;
import com.lifeos.util.DateSupport;
import com.lifeos.util.RecurrenceRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**

/**
 * Task lifecycle: CRUD, dependencies, recurrence, board ordering and bulk operations.
 * Ownership is enforced by every query, so a task id from another account yields 404, never its data.
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);
    private static final int MAX_OCCURRENCE_GENERATION = 60;

    private final TaskRepository taskRepository;
    private final TaskDependencyRepository dependencyRepository;
    private final GoalMilestoneRepository milestoneRepository;
    private final GoalRepository goalRepository;
    private final ProjectRepository projectRepository;
    private final LifeGraphService lifeGraphService;
    private final MetricsService metricsService;
    private final NotificationService notificationService;
    private final UserZoneService userZoneService;

    public TaskService(TaskRepository taskRepository,
                       TaskDependencyRepository dependencyRepository,
                       GoalMilestoneRepository milestoneRepository,
                      GoalRepository goalRepository,
                      ProjectRepository projectRepository,
                       LifeGraphService lifeGraphService,
                       MetricsService metricsService,
                       NotificationService notificationService,
                       UserZoneService userZoneService) {
        this.taskRepository = taskRepository;
        this.dependencyRepository = dependencyRepository;
        this.milestoneRepository = milestoneRepository;
        this.goalRepository = goalRepository;
        this.projectRepository = projectRepository;
        this.lifeGraphService = lifeGraphService;
        this.metricsService = metricsService;
        this.notificationService = notificationService;
        this.userZoneService = userZoneService;
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public Page<TaskDtos.TaskResponse> search(String userId, TaskQuery query) {
        ZoneId zone = ZoneId.of(query.zoneIdOrDefault());
        Specification<Task> spec = Specification.allOf(
                TaskSpecifications.ownedBy(userId),
                TaskSpecifications.withStatuses(query.statuses()),
                TaskSpecifications.withPriorities(query.priorities()),
                TaskSpecifications.withCategory(query.category()),
                TaskSpecifications.withGoalId(query.goalId()),
                TaskSpecifications.withProjectId(query.projectId()),
                TaskSpecifications.withMilestoneId(query.milestoneId()),
                TaskSpecifications.containingTag(query.tag()),
                TaskSpecifications.matchingText(query.text()),
                TaskSpecifications.overdueAt(query.isOverdueOnly() ? Instant.now() : null));

        Instant from = query.deadlineFrom() == null ? null : DateSupport.startOfDay(query.deadlineFrom(), zone);
        Instant to = query.deadlineTo() == null ? null : DateSupport.endOfDay(query.deadlineTo(), zone);
        spec = Specification.allOf(spec, TaskSpecifications.withDeadlineRange(from, to));

        // Deadline and priority orderings push dateless tasks to the end through a Criteria CASE
        // expression inside the specification, because a raw SQL sort fragment cannot survive the
        // specification query path. Those cases therefore run unsorted and let the spec order the page.
        if (requiresSpecificationOrdering(query.sort())) {
            spec = Specification.allOf(spec, orderingSpecification(query.sort()));
            Page<Task> page = taskRepository.findAll(
                    spec, PageRequest.of(Math.max(0, query.page()), pageSize(query.size()), Sort.unsorted()));
            return page.map(this::toResponse);
        }

        Pageable pageable = PageRequest.of(Math.max(0, query.page()), pageSize(query.size()), sortOf(query.sort()));
        Page<Task> page = taskRepository.findAll(spec, pageable);
        return page.map(this::toResponse);
    }

    private boolean requiresSpecificationOrdering(String sort) {
        if (sort == null) {
            return false;
        }
        String normalized = sort.toLowerCase(java.util.Locale.ROOT);
        return "deadline".equals(normalized) || "priority".equals(normalized);
    }

    private Specification<Task> orderingSpecification(String sort) {
        return "priority".equals(sort.toLowerCase(java.util.Locale.ROOT))
                ? TaskSpecifications.orderByPriorityThenDeadline()
                : TaskSpecifications.orderByDeadlineNullsLast();
    }

    @Transactional(readOnly = true)
    public TaskDtos.TaskResponse get(String userId, String taskId) {
        return toResponse(requireOwned(userId, taskId));
    }

    @Transactional(readOnly = true)
    public Task requireOwned(String userId, String taskId) {
        return taskRepository.findByIdAndUserIdAndDeletedAtIsNull(taskId, userId)
                .orElseThrow(() -> AppException.notFound("Task not found"));
    }

    @Transactional(readOnly = true)
    public List<TaskDtos.TaskBoardColumn> board(String userId) {
        List<Task> tasks = taskRepository.findAll(
                Specification.allOf(TaskSpecifications.ownedBy(userId), TaskSpecifications.all()),
                Sort.by(Sort.Order.asc("position"), Sort.Order.desc("createdAt")));
        List<TaskDtos.TaskResponse> responses = tasks.stream().map(this::toResponse).toList();
        List<TaskDtos.TaskBoardColumn> columns = List.of(
                new TaskDtos.TaskBoardColumn(TaskStatus.TODO, "To do", filter(responses, TaskStatus.TODO)),
                new TaskDtos.TaskBoardColumn(TaskStatus.IN_PROGRESS, "In progress", filter(responses, TaskStatus.IN_PROGRESS)),
                new TaskDtos.TaskBoardColumn(TaskStatus.COMPLETED, "Completed", filter(responses, TaskStatus.COMPLETED)),
                new TaskDtos.TaskBoardColumn(TaskStatus.CANCELLED, "Cancelled", filter(responses, TaskStatus.CANCELLED)));
        return columns;
    }

    @Transactional(readOnly = true)
    public List<Task> dueOn(String userId, LocalDate date, ZoneId zone) {
        return taskRepository.findDueBetween(userId, List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS),
                DateSupport.startOfDay(date, zone), DateSupport.endOfDay(date, zone));
    }

    @Transactional(readOnly = true)
    public List<Task> overdue(String userId, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        return taskRepository.findDueBetween(userId, List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS),
                DateSupport.startOfDay(LocalDate.of(1970, 1, 1), zone), DateSupport.startOfDay(today, zone));
    }

    // ----------------------------------------------------------------- writes

    @Transactional
    public TaskDtos.TaskResponse create(String userId, TaskDtos.TaskRequest request) {
        validateDeadlines(request.deadline(), request.recurrenceEndDate());
        Task task = new Task();
        task.setUserId(userId);
        apply(userId, task, request);
        if (task.getStatus() == null) {
            task.setStatus(TaskStatus.TODO);
        }
        if (request.position() != null) {
            task.setPosition(request.position());
        }
        taskRepository.save(task);

        replaceDependencies(userId, task.getId(), request.dependsOnTaskIds());
        syncGraphEdges(userId, task);
        metricsService.recordTaskCreated(userId, task);
        return toResponse(task);
    }

    @Transactional
    public TaskDtos.TaskResponse update(String userId, String taskId, TaskDtos.TaskRequest request) {
        Task task = requireOwned(userId, taskId);
        validateDeadlines(request.deadline(), request.recurrenceEndDate());
        if (request.milestoneId() != null && milestoneRepository.findByIdAndUserId(request.milestoneId(), userId).isEmpty()) {
            throw AppException.badRequest("The referenced milestone does not exist");
        }
        TaskStatus previousStatus = task.getStatus();
        apply(userId, task, request);
        taskRepository.save(task);

        if (request.dependsOnTaskIds() != null) {
            replaceDependencies(userId, task.getId(), request.dependsOnTaskIds());
        }
        syncGraphEdges(userId, task);

        if (previousStatus != task.getStatus()) {
            metricsService.recordStatusChange(userId, task, previousStatus);
        }
        return toResponse(task);
    }

    @Transactional
    public TaskDtos.TaskResponse changeStatus(String userId, String taskId, TaskDtos.StatusUpdateRequest request) {
        Task task = requireOwned(userId, taskId);
        TaskStatus previous = task.getStatus();
        applyStatus(task, request.status());
        if (request.actualMinutes() != null) {
            task.setActualMinutes(request.actualMinutes());
        }
        taskRepository.save(task);
        metricsService.recordStatusChange(userId, task, previous);
        syncGraphEdges(userId, task);
        return toResponse(task);
    }

    @Transactional
    public TaskDtos.TaskResponse complete(String userId, String taskId, TaskDtos.CompleteRequest request) {
        Task task = requireOwned(userId, taskId);
        assertNotBlocked(task, userId);
        applyStatus(task, TaskStatus.COMPLETED);
        if (request.actualMinutes() != null) {
            task.setActualMinutes(request.actualMinutes());
        } else if (task.getActualMinutes() == 0 && task.getEstimatedMinutes() != null) {
            task.setActualMinutes(task.getEstimatedMinutes());
        }
        if (request.notes() != null && !request.notes().isBlank()) {
            task.setNotes(request.notes());
        }
        taskRepository.save(task);
        metricsService.recordStatusChange(userId, task, TaskStatus.TODO);
        syncGraphEdges(userId, task);
        lifeGraphService.deriveEdgesFromCompletion(userId, task);
        return toResponse(task);
    }

    @Transactional
    public void delete(String userId, String taskId) {
        Task task = requireOwned(userId, taskId);
        List<String> dependents = dependencyRepository.findDependentIds(taskId);
        if (!dependents.isEmpty()) {
            taskRepository.softDelete(taskId, userId, Instant.now());
        } else {
            taskRepository.delete(task);
        }
        dependencyRepository.deleteByTaskId(taskId);
        lifeGraphService.removeNode(userId, GraphNodeType.TASK, taskId);
        log.debug("Task {} deleted by user {}", taskId, userId);
    }

    @Transactional
    public List<TaskDtos.TaskResponse> reorder(String userId, TaskDtos.ReorderRequest request) {
        List<Task> saved = new ArrayList<>();
        for (TaskDtos.PositionedTask positioned : request.tasks()) {
            Task task = requireOwned(userId, positioned.id());
            task.setPosition(positioned.position() == null ? 0 : positioned.position());
            saved.add(task);
        }
        taskRepository.saveAll(saved);
        return taskRepository.findAll(
                        Specification.allOf(TaskSpecifications.ownedBy(userId), TaskSpecifications.all()),
                        Sort.by(Sort.Order.asc("position"), Sort.Order.desc("createdAt")))
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public TaskDtos.BulkActionResult bulk(String userId, TaskDtos.BulkActionRequest request) {
        List<String> failed = new ArrayList<>();
        int affected = 0;
        for (String id : request.taskIds()) {
            try {
                Task task = taskRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId).orElse(null);
                if (task == null) {
                    failed.add(id);
                    continue;
                }
                switch (request.action()) {
                    case COMPLETE -> {
                        applyStatus(task, TaskStatus.COMPLETED);
                        metricsService.recordStatusChange(userId, task, task.getStatus());
                    }
                    case REOPEN -> {
                        applyStatus(task, TaskStatus.TODO);
                        metricsService.recordStatusChange(userId, task, TaskStatus.COMPLETED);
                    }
                    case CANCEL -> applyStatus(task, TaskStatus.CANCELLED);
                    case DELETE -> {
                        taskRepository.softDelete(task.getId(), userId, Instant.now());
                        lifeGraphService.removeNode(userId, GraphNodeType.TASK, task.getId());
                    }
                    case SET_PRIORITY -> {
                        if (request.priority() == null) {
                            throw AppException.badRequest("priority is required for this action");
                        }
                        task.setPriority(request.priority());
                    }
case MOVE_TO_GOAL -> task.setGoalId(requireOwnedGoal(userId, request.goalId()));
                    case MOVE_TO_PROJECT -> task.setProjectId(
                            requireOwnedProject(userId, request.projectId()));
                    case SET_CATEGORY -> task.setCategory(request.category());
                }
                taskRepository.save(task);
                syncGraphEdges(userId, task);
                affected++;
            } catch (RuntimeException ex) {
                log.warn("Bulk action {} failed for task {}: {}", request.action(), id, ex.getMessage());
                failed.add(id);
            }
        }
        return new TaskDtos.BulkActionResult(affected, failed,
                affected + " task(s) updated" + (failed.isEmpty() ? "" : ", " + failed.size() + " skipped"));
    }

    // ----------------------------------------------------------- dependencies

    @Transactional(readOnly = true)
    public List<String> dependencies(String userId, String taskId) {
        requireOwned(userId, taskId);
        return dependencyRepository.findDependsOnIds(taskId);
    }

    @Transactional
    public List<String> addDependency(String userId, String taskId, String dependsOnTaskId) {
        Task task = requireOwned(userId, taskId);
        requireOwned(userId, dependsOnTaskId);
        if (taskId.equals(dependsOnTaskId)) {
            throw AppException.badRequest("A task cannot depend on itself");
        }
        assertNoCycle(userId, taskId, dependsOnTaskId);
        if (!dependencyRepository.findDependsOnIds(taskId).contains(dependsOnTaskId)) {
            TaskDependency dependency = new TaskDependency();
            dependency.setTaskId(taskId);
            dependency.setDependsOnTaskId(dependsOnTaskId);
            dependencyRepository.save(dependency);
            lifeGraphService.link(userId, GraphNodeType.TASK, dependsOnTaskId, GraphNodeType.TASK, taskId,
                    GraphRelation.DEPENDS_ON, 1.0);
        }
        return dependencyRepository.findDependsOnIds(taskId);
    }

    @Transactional
    public List<String> removeDependency(String userId, String taskId, String dependsOnTaskId) {
        requireOwned(userId, taskId);
        dependencyRepository.findByTaskId(taskId).stream()
                .filter(dependency -> dependency.getDependsOnTaskId().equals(dependsOnTaskId))
                .forEach(dependencyRepository::delete);
        lifeGraphService.unlink(userId, GraphNodeType.TASK, dependsOnTaskId, GraphNodeType.TASK, taskId,
                GraphRelation.DEPENDS_ON);
        return dependencyRepository.findDependsOnIds(taskId);
    }

    // -------------------------------------------------------------- recurrence

    /**
     * Materialises the next occurrences of a recurring task as real rows so they appear on the
     * board and in the plan. Runs on creation and is safe to call again; existing rows are skipped.
     */
    @Transactional
    public int materialiseRecurrence(String userId, String taskId) {
        Task origin = requireOwned(userId, taskId);
        if (!RecurrenceRule.isRecurring(origin.getRecurrenceRule()) || origin.getDeadline() == null) {
            return 0;
        }
        ZoneId zone = ZoneId.of(currentZone(userId));
        RecurrenceRule rule = RecurrenceRule.parse(origin.getRecurrenceRule());
        Instant hardEnd = origin.getRecurrenceEndDate() != null
                ? origin.getRecurrenceEndDate()
                : Instant.now().plusSeconds(60L * 60 * 24 * 90);
        Instant windowEnd = hardEnd.isBefore(Instant.now().plusSeconds(60L * 60 * 24 * 30))
                ? hardEnd
                : Instant.now().plusSeconds(60L * 60 * 24 * 30);

        List<Instant> occurrences = rule.expand(origin.getDeadline(), Instant.now(), windowEnd, zone, hardEnd,
                MAX_OCCURRENCE_GENERATION);
        if (occurrences.isEmpty()) {
            return 0;
        }
        Set<String> existing = taskRepository.findByUserIdAndRecurrenceParentIdAndDeletedAtIsNull(userId, origin.getId())
                .stream()
                .map(task -> task.getDeadline() == null ? "" : task.getDeadline().toString())
                .collect(Collectors.toSet());

        int created = 0;
        for (Instant occurrence : occurrences) {
            if (existing.contains(occurrence.toString())) {
                continue;
            }
            Task clone = new Task();
            clone.setUserId(userId);
            clone.setTitle(origin.getTitle());
            clone.setDescription(origin.getDescription());
            clone.setNotes(origin.getNotes());
            clone.setStatus(TaskStatus.TODO);
            clone.setPriority(origin.getPriority());
            clone.setCategory(origin.getCategory());
            clone.setEstimatedMinutes(origin.getEstimatedMinutes());
            clone.setDifficulty(origin.getDifficulty());
            clone.setEnergyRequirement(origin.getEnergyRequirement());
            clone.setTags(origin.getTags());
            clone.setGoalId(origin.getGoalId());
            clone.setProjectId(origin.getProjectId());
            clone.setRecurrenceParentId(origin.getId());
            clone.setRecurrenceRule(null);
            clone.setPosition(origin.getPosition());
            clone.setDeadline(occurrence);
            taskRepository.save(clone);
            created++;
        }
        return created;
    }

    // ---------------------------------------------------------------- mapping

    public TaskDtos.TaskResponse toResponse(Task task) {
        List<TaskDtos.DependencyResponse> blocks = dependencyRepository.findDependentIds(task.getId()).stream()
                .map(id -> taskRepository.findByIdAndUserIdAndDeletedAtIsNull(id, task.getUserId())
                        .map(dep -> new TaskDtos.DependencyResponse(dep.getId(), dep.getTitle(), dep.getStatus()))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();

        return new TaskDtos.TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getNotes(),
                task.getStatus(),
                task.getPriority(),
                task.getCategory(),
                task.getDeadline(),
                task.getEstimatedMinutes(),
                task.getActualMinutes(),
                task.getDifficulty(),
                task.getEnergyRequirement(),
                Csv.splitToList(task.getTags()),
                task.getGoalId(),
                task.getMilestoneId(),
                task.getProjectId(),
                task.getRecurrenceRule(),
                task.getRecurrenceParentId(),
                task.getRecurrenceEndDate(),
                task.getPosition(),
                task.getCompletedAt(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                dependencyRepository.findDependsOnIds(task.getId()),
                blocks);
    }

    // ---------------------------------------------------------------- helpers

    private void apply(String userId, Task task, TaskDtos.TaskRequest request) {
        task.setTitle(request.title().strip());
        if (request.description() != null) {
            task.setDescription(request.description());
        }
        if (request.notes() != null) {
            task.setNotes(request.notes());
        }
        if (request.status() != null) {
            applyStatus(task, request.status());
        }
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        if (request.category() != null) {
            task.setCategory(request.category().isBlank() ? null : request.category().trim());
        }
        if (request.deadline() != null || request.status() != null) {
            task.setDeadline(request.deadline());
        }
        if (request.estimatedMinutes() != null) {
            task.setEstimatedMinutes(request.estimatedMinutes());
        }
        if (request.actualMinutes() != null) {
            task.setActualMinutes(request.actualMinutes());
        }
        if (request.difficulty() != null) {
            task.setDifficulty(request.difficulty());
        }
        if (request.energyRequirement() != null) {
            task.setEnergyRequirement(request.energyRequirement());
        }
        if (request.tags() != null) {
            task.setTags(Csv.join(request.tags()));
        }
        if (request.goalId() != null) {
            task.setGoalId(requireOwnedGoal(userId, request.goalId()));
        }
        if (request.milestoneId() != null) {
            task.setMilestoneId(requireOwnedMilestone(userId, request.milestoneId()));
        }
        if (request.projectId() != null) {
            task.setProjectId(requireOwnedProject(userId, request.projectId()));
        }
        if (request.recurrenceRule() != null) {
            task.setRecurrenceRule(request.recurrenceRule().isBlank() ? null : request.recurrenceRule().trim());
        }
        if (request.recurrenceEndDate() != null) {
            task.setRecurrenceEndDate(request.recurrenceEndDate());
        }
    }

    /**
 * Links may only point at the caller's own records. A blank clears the link; an id that does not belong
 * to the caller is reported as missing, which is also what keeps a task from being filed under another
 * account's goal.
 */
private String requireOwnedGoal(String userId, String goalId) {
        if (goalId == null || goalId.isBlank()) {
            return null;
        }
        goalRepository.findByIdAndUserIdAndDeletedAtIsNull(goalId, userId)
                .orElseThrow(() -> AppException.notFound("Goal not found"));
        return goalId;
    }

    private String requireOwnedMilestone(String userId, String milestoneId) {
        if (milestoneId == null || milestoneId.isBlank()) {
            return null;
        }
        milestoneRepository.findByIdAndUserId(milestoneId, userId)
                .orElseThrow(() -> AppException.notFound("Milestone not found"));
        return milestoneId;
    }

    private String requireOwnedProject(String userId, String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        projectRepository.findByIdAndUserIdAndDeletedAtIsNull(projectId, userId)
                .orElseThrow(() -> AppException.notFound("Project not found"));
        return projectId;
    }

    private void applyStatus(Task task, TaskStatus status) {
        task.setStatus(status);
        if (status == TaskStatus.COMPLETED) {
            if (task.getCompletedAt() == null) {
                task.setCompletedAt(Instant.now());
            }
        } else {
            task.setCompletedAt(null);
        }
    }

    private void replaceDependencies(String userId, String taskId, List<String> dependsOnTaskIds) {
        dependencyRepository.findByTaskId(taskId).forEach(dependencyRepository::delete);
        dependencyRepository.flush();
        if (dependsOnTaskIds != null) {
            dependsOnTaskIds.stream().distinct().forEach(other -> addDependency(userId, taskId, other));
        }
    }

    private void assertNotBlocked(Task task, String userId) {
        List<String> blockers = dependencyRepository.findDependsOnIds(task.getId());
        if (blockers.isEmpty()) {
            return;
        }
        List<Task> blocking = blockers.stream()
                .map(id -> taskRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId).orElse(null))
                .filter(java.util.Objects::nonNull)
                .filter(dep -> dep.getStatus() != TaskStatus.COMPLETED && dep.getStatus() != TaskStatus.CANCELLED)
                .toList();
        if (!blocking.isEmpty()) {
            String names = blocking.stream().map(Task::getTitle).limit(3).collect(Collectors.joining(", "));
            throw AppException.conflict("Blocked by " + blocking.size() + " unfinished task(s): " + names);
        }
    }

    /** Depth-first search from {@code candidate}, failing if it can already reach {@code taskId}. */
    private void assertNoCycle(String userId, String taskId, String candidateTaskId) {
        Set<String> visited = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(candidateTaskId);
        while (!stack.isEmpty()) {
            String current = stack.pop();
            if (!visited.add(current)) {
                continue;
            }
            if (current.equals(taskId)) {
                throw AppException.badRequest("That dependency would create a circular reference");
            }
            if (visited.size() > 500) {
                break;
            }
            stack.addAll(dependencyRepository.findDependsOnIds(current));
        }
    }

    private void syncGraphEdges(String userId, Task task) {
        if (task.getGoalId() != null) {
            lifeGraphService.link(userId, GraphNodeType.TASK, task.getId(), GraphNodeType.GOAL, task.getGoalId(),
                    GraphRelation.CONTRIBUTES_TO, 1.0);
        }
        if (task.getProjectId() != null) {
            lifeGraphService.link(userId, GraphNodeType.TASK, task.getId(), GraphNodeType.PROJECT, task.getProjectId(),
                    GraphRelation.RELATED_TO, 1.0);
        }
    }

    private void validateDeadlines(Instant deadline, Instant recurrenceEndDate) {
        if (deadline != null && recurrenceEndDate != null && recurrenceEndDate.isBefore(deadline)) {
            throw AppException.badRequest("The recurrence end date must be on or after the first deadline");
        }
    }

    private String currentZone(String userId) {
        return userZoneService.zoneOf(userId).getId();
    }

    private List<TaskDtos.TaskResponse> filter(List<TaskDtos.TaskResponse> responses, TaskStatus status) {
        return responses.stream().filter(task -> task.status() == status).toList();
    }

    private int pageSize(Integer size) {
        return size == null || size <= 0 ? 20 : Math.min(size, 100);
    }

/**
 * Maps the public sort keys onto plain mapped properties. Deadline and priority ordering is not handled
 * here; it is applied through {@link TaskSpecifications#orderByDeadlineNullsLast()} and
 * {@link TaskSpecifications#orderByPriorityThenDeadline()} so that rows without a deadline sort last.
 */
private Sort sortOf(String sort) {
        if (sort == null) {
            return Sort.by(Sort.Order.asc("position"), Sort.Order.desc("createdAt"));
        }
        return switch (sort.toLowerCase(java.util.Locale.ROOT)) {
            case "title" -> Sort.by(Sort.Order.asc("title"));
            case "created" -> Sort.by(Sort.Order.desc("createdAt"));
            case "updated" -> Sort.by(Sort.Order.desc("updatedAt"));
            case "position" -> Sort.by(Sort.Order.asc("position"), Sort.Order.desc("createdAt"));
            default -> Sort.by(Sort.Order.asc("position"), Sort.Order.desc("createdAt"));
        };
    }
}