package com.lifeos.service;

import com.lifeos.dto.GoalDtos;
import com.lifeos.entity.Goal;
import com.lifeos.entity.GoalMilestone;
import com.lifeos.entity.Task;
import com.lifeos.entity.enums.GraphNodeType;
import com.lifeos.entity.enums.GraphRelation;
import com.lifeos.entity.enums.GoalStatus;
import com.lifeos.entity.enums.GoalType;
import com.lifeos.entity.enums.Origin;
import com.lifeos.entity.enums.Priority;
import com.lifeos.entity.enums.TaskStatus;
import com.lifeos.exception.AppException;
import com.lifeos.repository.GoalMilestoneRepository;
import com.lifeos.repository.GoalRepository;
import com.lifeos.repository.TaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Goals, sub-goals and milestones. Progress is derived from milestones when they exist. */
@Service
public class GoalService {

    private final GoalRepository goalRepository;
    private final GoalMilestoneRepository milestoneRepository;
    private final TaskRepository taskRepository;
    private final LifeGraphService lifeGraphService;

    public GoalService(GoalRepository goalRepository,
                       GoalMilestoneRepository milestoneRepository,
                       TaskRepository taskRepository,
                       LifeGraphService lifeGraphService) {
        this.goalRepository = goalRepository;
        this.milestoneRepository = milestoneRepository;
        this.taskRepository = taskRepository;
        this.lifeGraphService = lifeGraphService;
    }

    @Transactional(readOnly = true)
    public Page<GoalDtos.GoalResponse> search(String userId, String text, GoalStatus status,
                                              int page, int size) {
        Page<GoalDtos.GoalResponse> result = goalRepository
                .search(userId, text, status, PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 100)))
                .map(this::toResponse);
        return result;
    }

    @Transactional(readOnly = true)
    public List<GoalDtos.GoalResponse> allActive(String userId) {
        return goalRepository.findByStatus(userId, GoalStatus.ACTIVE).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public GoalDtos.GoalResponse get(String userId, String goalId) {
        return toResponse(requireOwned(userId, goalId));
    }

    @Transactional(readOnly = true)
    public Goal requireOwned(String userId, String goalId) {
        return goalRepository.findByIdAndUserIdAndDeletedAtIsNull(goalId, userId)
                .orElseThrow(() -> AppException.notFound("Goal not found"));
    }

    @Transactional
    public GoalDtos.GoalResponse create(String userId, GoalDtos.GoalRequest request, Origin origin, boolean aiConfirmed) {
        validateParent(userId, request.parentGoalId(), null);
        Goal goal = new Goal();
        goal.setUserId(userId);
        apply(goal, request);
        goal.setSource(origin == null ? Origin.USER : origin);
        goal.setAiConfirmed(origin == Origin.USER || aiConfirmed);
        goal.setStatus(request.status() == null ? GoalStatus.ACTIVE : request.status());
        goalRepository.save(goal);

        if (request.milestones() != null) {
            replaceMilestones(userId, goal, request.milestones());
        }
        if (goal.getParentGoalId() != null) {
            lifeGraphService.link(userId, GraphNodeType.GOAL, goal.getId(), GraphNodeType.GOAL,
                    goal.getParentGoalId(), GraphRelation.CONTRIBUTES_TO, 1.0);
        }
        return toResponse(goal);
    }

    @Transactional
    public GoalDtos.GoalResponse update(String userId, String goalId, GoalDtos.GoalRequest request) {
        Goal goal = requireOwned(userId, goalId);
        validateParent(userId, request.parentGoalId(), goalId);
        apply(goal, request);
        goalRepository.save(goal);
        if (request.milestones() != null) {
            replaceMilestones(userId, goal, request.milestones());
            goal.setProgress(deriveProgress(goal));
            goalRepository.save(goal);
        }
        return toResponse(goal);
    }

    @Transactional
    public GoalDtos.GoalResponse updateProgress(String userId, String goalId, GoalDtos.ProgressUpdateRequest request) {
        Goal goal = requireOwned(userId, goalId);
        if (request.progress() != null) {
            goal.setProgress(Math.max(0, Math.min(100, request.progress())));
            if (goal.getProgress() >= 100 && goal.getStatus() == GoalStatus.ACTIVE) {
                goal.setStatus(GoalStatus.ACHIEVED);
            }
        }
        if (request.status() != null) {
            goal.setStatus(request.status());
        }
        goalRepository.save(goal);
        return toResponse(goal);
    }

    @Transactional
    public void delete(String userId, String goalId) {
        Goal goal = requireOwned(userId, goalId);
        long subGoals = goalRepository.countSubGoals(userId, goalId);
        if (subGoals > 0) {
            goal.setDeletedAt(Instant.now());
            goal.setStatus(GoalStatus.ARCHIVED);
            goalRepository.save(goal);
        } else {
            goalRepository.delete(goal);
        }
        lifeGraphService.removeNode(userId, GraphNodeType.GOAL, goalId);
    }

    @Transactional
    public GoalDtos.MilestoneResponse createMilestone(String userId, String goalId, GoalDtos.MilestoneRequest request) {
        Goal goal = requireOwned(userId, goalId);
        GoalMilestone milestone = new GoalMilestone();
        milestone.setGoalId(goalId);
        milestone.setUserId(userId);
        applyMilestone(milestone, request);
        milestone.setPosition(request.position() != null ? request.position()
                : milestoneRepository.findByGoalIdOrderByPositionAsc(goalId).size());
        milestoneRepository.save(milestone);
        refreshGoalProgress(goal);
        return toMilestoneResponse(milestone);
    }

    @Transactional
    public GoalDtos.MilestoneResponse updateMilestone(String userId, String milestoneId, GoalDtos.MilestoneRequest request) {
        GoalMilestone milestone = milestoneRepository.findByIdAndUserId(milestoneId, userId)
                .orElseThrow(() -> AppException.notFound("Milestone not found"));
        applyMilestone(milestone, request);
        milestoneRepository.save(milestone);
        goalRepository.findByIdAndUserIdAndDeletedAtIsNull(milestone.getGoalId(), userId)
                .ifPresent(this::refreshGoalProgress);
        return toMilestoneResponse(milestone);
    }

    @Transactional
    public void deleteMilestone(String userId, String milestoneId) {
        GoalMilestone milestone = milestoneRepository.findByIdAndUserId(milestoneId, userId)
                .orElseThrow(() -> AppException.notFound("Milestone not found"));
        milestoneRepository.delete(milestone);
        goalRepository.findByIdAndUserIdAndDeletedAtIsNull(milestone.getGoalId(), userId)
                .ifPresent(this::refreshGoalProgress);
    }

    @Transactional(readOnly = true)
    public List<GoalDtos.MilestoneResponse> milestones(String userId, String goalId) {
        requireOwned(userId, goalId);
        return milestoneRepository.findByGoalIdOrderByPositionAsc(goalId).stream()
                .map(this::toMilestoneResponse).toList();
    }

    /** Persists an AI proposal only after the user has reviewed and confirmed it. */
    @Transactional
    public GoalDtos.GoalResponse confirmProposal(String userId, GoalDtos.ConfirmProposalRequest request) {
        GoalDtos.GoalResponse created = create(userId, request.goal(), Origin.USER, true);
        if (request.milestones() != null && !request.milestones().isEmpty()) {
            Goal fresh = requireOwned(userId, created.id());
            replaceMilestones(userId, fresh, request.milestones());
            refreshGoalProgress(fresh);
        }
        return toResponse(requireOwned(userId, created.id()));
    }

    public GoalDtos.GoalResponse toResponse(Goal goal) {
        List<GoalMilestone> milestones = milestoneRepository.findByGoalIdOrderByPositionAsc(goal.getId());
        List<Task> tasks = taskRepository.findByGoalIds(goal.getUserId(), List.of(goal.getId()));
        long completedTasks = tasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.COMPLETED)
                .count();
        List<GoalDtos.GoalResponse> subGoals = goalRepository.findAllForUser(goal.getUserId()).stream()
                .filter(candidate -> goal.getId().equals(candidate.getParentGoalId()))
                .map(this::toResponse)
                .toList();

        long completedMilestones = milestones.stream().filter(GoalMilestone::isCompleted).count();

        return new GoalDtos.GoalResponse(
                goal.getId(),
                goal.getTitle(),
                goal.getDescription(),
                goal.getCategory(),
                goal.getGoalType(),
                goal.getTargetDate(),
                goal.getProgress(),
                goal.getPriority(),
                goal.getStatus(),
                goal.getColor(),
                goal.getSource(),
                goal.isAiConfirmed(),
                goal.getParentGoalId(),
                goal.getPosition(),
                goal.getCreatedAt(),
                goal.getUpdatedAt(),
                tasks.size(),
                completedTasks,
                milestones.size(),
                completedMilestones,
                milestones.stream().map(this::toMilestoneResponse).toList(),
                subGoals);
    }

    // ---------------------------------------------------------------- helpers

    private void apply(Goal goal, GoalDtos.GoalRequest request) {
        goal.setTitle(request.title().strip());
        if (request.description() != null) {
            goal.setDescription(request.description());
        }
        goal.setCategory(request.category());
        if (request.goalType() != null) {
            goal.setGoalType(request.goalType());
        }
        goal.setTargetDate(request.targetDate());
        if (request.progress() != null) {
            goal.setProgress(Math.max(0, Math.min(100, request.progress())));
        }
        if (request.priority() != null) {
            goal.setPriority(request.priority());
        }
        if (request.status() != null) {
            goal.setStatus(request.status());
        }
        goal.setColor(request.color());
        goal.setParentGoalId(request.parentGoalId() == null || request.parentGoalId().isBlank()
                ? null : request.parentGoalId());
        if (request.position() != null) {
            goal.setPosition(request.position());
        }
    }

    private void applyMilestone(GoalMilestone milestone, GoalDtos.MilestoneRequest request) {
        milestone.setTitle(request.title().strip());
        if (request.description() != null) {
            milestone.setDescription(request.description());
        }
        milestone.setDueDate(request.dueDate());
        if (request.completed() != null) {
            milestone.setCompleted(request.completed());
        }
        if (request.progress() != null) {
            milestone.setProgress(Math.max(0, Math.min(100, request.progress())));
        }
        if (request.position() != null) {
            milestone.setPosition(request.position());
        }
    }

    private void replaceMilestones(String userId, Goal goal, List<GoalDtos.MilestoneRequest> requests) {
        milestoneRepository.deleteByGoalId(goal.getId());
        milestoneRepository.flush();
        int position = 0;
        for (GoalDtos.MilestoneRequest request : requests) {
            GoalMilestone milestone = new GoalMilestone();
            milestone.setGoalId(goal.getId());
            milestone.setUserId(userId);
            applyMilestone(milestone, request);
            milestone.setPosition(position++);
            milestoneRepository.save(milestone);
        }
    }

    private void refreshGoalProgress(Goal goal) {
        goal.setProgress(deriveProgress(goal));
        goalRepository.save(goal);
    }

    private int deriveProgress(Goal goal) {
        List<GoalMilestone> milestones = milestoneRepository.findByGoalIdOrderByPositionAsc(goal.getId());
        if (milestones.isEmpty()) {
            return goal.getProgress();
        }
        long total = milestones.stream().mapToLong(m -> m.isCompleted() ? 100 : m.getProgress()).sum();
        return (int) Math.round(total / milestones.size());
    }

    private void validateParent(String userId, String parentGoalId, String selfId) {
        if (parentGoalId == null || parentGoalId.isBlank()) {
            return;
        }
        if (parentGoalId.equals(selfId)) {
            throw AppException.badRequest("A goal cannot be its own parent");
        }
        requireOwned(userId, parentGoalId);
    }

    private GoalDtos.MilestoneResponse toMilestoneResponse(GoalMilestone milestone) {
        return new GoalDtos.MilestoneResponse(
                milestone.getId(),
                milestone.getGoalId(),
                milestone.getTitle(),
                milestone.getDescription(),
                milestone.getDueDate(),
                milestone.isCompleted(),
                milestone.getProgress(),
                milestone.getPosition());
    }

    /** Aggregated counts used by the dashboard without loading full goal objects. */
    @Transactional(readOnly = true)
    public Map<String, long[]> progressByGoal(String userId) {
        Map<String, long[]> result = new HashMap<>();
        for (Goal goal : goalRepository.findByStatus(userId, GoalStatus.ACTIVE)) {
            List<Task> tasks = taskRepository.findByGoalIds(userId, List.of(goal.getId()));
            long total = tasks.size();
            long completed = tasks.stream().filter(task -> task.getStatus() == TaskStatus.COMPLETED).count();
            result.put(goal.getId(), new long[]{total, completed});
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<GoalDtos.GoalSummary> summaries(String userId) {
        return goalRepository.findByStatus(userId, GoalStatus.ACTIVE).stream()
                .map(goal -> {
                    List<Task> tasks = taskRepository.findByGoalIds(userId, List.of(goal.getId()));
                    long completed = tasks.stream().filter(task -> task.getStatus() == TaskStatus.COMPLETED).count();
                    return new GoalDtos.GoalSummary(goal.getId(), goal.getTitle(), goal.getProgress(),
                            goal.getStatus(), goal.getTargetDate(), tasks.size(), completed);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GoalDtos.GoalSummary> upcoming(String userId, Instant from, Instant to) {
        return goalRepository.findWithTargetDateBetween(userId, from, to).stream()
                .map(goal -> {
                    List<Task> tasks = taskRepository.findByGoalIds(userId, List.of(goal.getId()));
                    long completed = tasks.stream().filter(task -> task.getStatus() == TaskStatus.COMPLETED).count();
                    return new GoalDtos.GoalSummary(goal.getId(), goal.getTitle(), goal.getProgress(),
                            goal.getStatus(), goal.getTargetDate(), tasks.size(), completed);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long activeCount(String userId) {
        return goalRepository.countByUserIdAndDeletedAtIsNullAndStatus(userId, GoalStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public long totalCount(String userId) {
        return goalRepository.countByUserIdAndDeletedAtIsNull(userId);
    }

    @Transactional(readOnly = true)
    public List<Goal> activeGoals(String userId) {
        return new ArrayList<>(goalRepository.findByStatus(userId, GoalStatus.ACTIVE));
    }
}