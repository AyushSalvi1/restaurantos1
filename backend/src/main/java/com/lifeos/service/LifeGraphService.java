package com.lifeos.service;

import com.lifeos.dto.AnalyticsDtos;
import com.lifeos.entity.Habit;
import com.lifeos.entity.LearningGoal;
import com.lifeos.entity.LearningTopic;
import com.lifeos.entity.LifeGraphEdge;
import com.lifeos.entity.Skill;
import com.lifeos.entity.Task;
import com.lifeos.entity.enums.GraphNodeType;
import com.lifeos.entity.enums.GraphRelation;
import com.lifeos.entity.enums.TaskStatus;
import com.lifeos.exception.AppException;
import com.lifeos.repository.CalendarEventRepository;
import com.lifeos.repository.FocusSessionRepository;
import com.lifeos.repository.GoalRepository;
import com.lifeos.repository.HabitRepository;
import com.lifeos.repository.LearningGoalRepository;
import com.lifeos.repository.LearningTopicRepository;
import com.lifeos.repository.LifeGraphEdgeRepository;
import com.lifeos.repository.ProjectRepository;
import com.lifeos.repository.SkillRepository;
import com.lifeos.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds and queries the relationship graph that ties goals, tasks, habits, skills, projects,
 * learning topics, calendar events and focus sessions together.
 *
 * <p>Edges are stored relationally so the graph survives restarts; node labels are resolved on read
 * so a renamed task never leaves a stale caption behind.</p>
 */
@Service
public class LifeGraphService {

    private static final int MAX_GRAPH_NODES = 400;

    private final LifeGraphEdgeRepository edgeRepository;
    private final GoalRepository goalRepository;
    private final TaskRepository taskRepository;
    private final HabitRepository habitRepository;
    private final ProjectRepository projectRepository;
    private final SkillRepository skillRepository;
    private final LearningGoalRepository learningGoalRepository;
    private final LearningTopicRepository learningTopicRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final FocusSessionRepository focusSessionRepository;

    public LifeGraphService(LifeGraphEdgeRepository edgeRepository,
                            GoalRepository goalRepository,
                            TaskRepository taskRepository,
                            HabitRepository habitRepository,
                            ProjectRepository projectRepository,
                            SkillRepository skillRepository,
                            LearningGoalRepository learningGoalRepository,
                            LearningTopicRepository learningTopicRepository,
                            CalendarEventRepository calendarEventRepository,
                            FocusSessionRepository focusSessionRepository) {
        this.edgeRepository = edgeRepository;
        this.goalRepository = goalRepository;
        this.taskRepository = taskRepository;
        this.habitRepository = habitRepository;
        this.projectRepository = projectRepository;
        this.skillRepository = skillRepository;
        this.learningGoalRepository = learningGoalRepository;
        this.learningTopicRepository = learningTopicRepository;
        this.calendarEventRepository = calendarEventRepository;
        this.focusSessionRepository = focusSessionRepository;
    }

    // ------------------------------------------------------------------ edges

    @Transactional
    public LifeGraphEdge link(String userId, GraphNodeType sourceType, String sourceId,
                              GraphNodeType targetType, String targetId, GraphRelation relation, double weight) {
        if (sourceId.equals(targetId) && sourceType == targetType) {
            return null;
        }
        Optional<LifeGraphEdge> existing = edgeRepository
                .findByUserIdAndSourceTypeAndSourceIdAndTargetTypeAndTargetIdAndRelation(
                        userId, sourceType, sourceId, targetType, targetId, relation);
        if (existing.isPresent()) {
            return existing.get();
        }
        LifeGraphEdge edge = new LifeGraphEdge();
        edge.setUserId(userId);
        edge.setSourceType(sourceType);
        edge.setSourceId(sourceId);
        edge.setTargetType(targetType);
        edge.setTargetId(targetId);
        edge.setRelation(relation);
        edge.setWeight(weight);
        return edgeRepository.save(edge);
    }

    @Transactional
    public void unlink(String userId, GraphNodeType sourceType, String sourceId,
                       GraphNodeType targetType, String targetId, GraphRelation relation) {
        edgeRepository.findByUserIdAndSourceTypeAndSourceId(userId, sourceType, sourceId).stream()
                .filter(edge -> edge.getTargetType() == targetType && edge.getTargetId().equals(targetId))
                .filter(edge -> edge.getRelation() == relation)
                .forEach(edgeRepository::delete);
    }

    @Transactional
    public void removeNode(String userId, GraphNodeType type, String nodeId) {
        edgeRepository.findAdjacent(userId, type, nodeId).forEach(edgeRepository::delete);
    }

    /** Completing a task strengthens the contribution of that task to its goal and project. */
    @Transactional
    public void deriveEdgesFromCompletion(String userId, Task task) {
        if (task.getGoalId() != null) {
            link(userId, GraphNodeType.TASK, task.getId(), GraphNodeType.GOAL, task.getGoalId(),
                    GraphRelation.CONTRIBUTES_TO, 1.5);
        }
        if (task.getProjectId() != null && task.getGoalId() != null) {
            link(userId, GraphNodeType.PROJECT, task.getProjectId(), GraphNodeType.GOAL, task.getGoalId(),
                    GraphRelation.SUPPORTS, 1.2);
        }
        if (task.getMilestoneId() != null) {
            link(userId, GraphNodeType.TASK, task.getId(), GraphNodeType.GOAL,
                    task.getGoalId() == null ? "" : task.getGoalId(), GraphRelation.DERIVED_FROM, 1.0);
        }
    }

    @Transactional
    public void connectLearningTopic(String userId, String topicId, String learningGoalId) {
        link(userId, GraphNodeType.LEARNING_TOPIC, topicId, GraphNodeType.LEARNING_GOAL, learningGoalId,
                GraphRelation.CONTRIBUTES_TO, 1.0);
        LearningTopic topic = learningTopicRepository.findById(topicId).orElse(null);
        LearningGoal goal = learningGoalRepository.findById(learningGoalId).orElse(null);
        if (topic != null && goal != null && goal.getSkillId() != null) {
            link(userId, GraphNodeType.LEARNING_GOAL, learningGoalId, GraphNodeType.SKILL, goal.getSkillId(),
                    GraphRelation.SUPPORTS, 1.0);
        }
    }

    // ------------------------------------------------------------------ query

    @Transactional(readOnly = true)
    public AnalyticsDtos.GraphResponse graph(String userId, List<GraphNodeType> types, String focusType, String focusId) {
        List<LifeGraphEdge> edges = edgeRepository.findByUserIdAndSourceTypeIn(
                userId, types == null || types.isEmpty() ? java.util.Arrays.asList(GraphNodeType.values()) : types);

        Map<String, AnalyticsDtos.GraphNode> nodes = new HashMap<>();
        for (LifeGraphEdge edge : edges) {
            nodes.computeIfAbsent(key(edge.getSourceType(), edge.getSourceId()),
                    ignored -> resolveNode(userId, edge.getSourceType(), edge.getSourceId()));
            nodes.computeIfAbsent(key(edge.getTargetType(), edge.getTargetId()),
                    ignored -> resolveNode(userId, edge.getTargetType(), edge.getTargetId()));
        }

        // The active goals and habits are the anchors of the graph even before anything links to them.
        goalRepository.findByStatus(userId, com.lifeos.entity.enums.GoalStatus.ACTIVE)
                .stream().limit(40)
                .forEach(goal -> nodes.putIfAbsent(key(GraphNodeType.GOAL, goal.getId()),
                        new AnalyticsDtos.GraphNode(goal.getId(), GraphNodeType.GOAL.name(), goal.getTitle(),
                                goal.getCategory(), goal.getStatus().name(), goal.getColor(), goal.getProgress(),
                                Map.of("progress", goal.getProgress(), "targetDate", String.valueOf(goal.getTargetDate())))));
        habitRepository.findActiveForUser(userId).stream().limit(40)
                .forEach(habit -> nodes.putIfAbsent(key(GraphNodeType.HABIT, habit.getId()),
                        new AnalyticsDtos.GraphNode(habit.getId(), GraphNodeType.HABIT.name(), habit.getName(),
                                habit.getCategory(), "ACTIVE", habit.getColor(), 1, Map.of())));

        List<AnalyticsDtos.GraphNode> nodeList = nodes.values().stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(AnalyticsDtos.GraphNode::type).thenComparing(AnalyticsDtos.GraphNode::label))
                .limit(MAX_GRAPH_NODES)
                .toList();

        Set<String> retained = nodeList.stream()
                .map(node -> node.type() + ":" + node.id())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<AnalyticsDtos.GraphEdge> edgeList = edges.stream()
                .filter(edge -> retained.contains(edge.getSourceType().name() + ":" + edge.getSourceId()))
                .filter(edge -> retained.contains(edge.getTargetType().name() + ":" + edge.getTargetId()))
                .map(edge -> new AnalyticsDtos.GraphEdge(
                        edge.getId(),
                        edge.getSourceType().name() + ":" + edge.getSourceId(),
                        edge.getTargetType().name() + ":" + edge.getTargetId(),
                        edge.getRelation().name(),
                        edge.getWeight()))
                .toList();

        List<AnalyticsDtos.GraphNode> neighbours = List.of();
        if (focusId != null && focusType != null) {
            neighbours = neighbours(userId, GraphNodeType.valueOf(focusType), focusId);
        }

        return new AnalyticsDtos.GraphResponse(
                nodeList,
                edgeList,
                nodeList.size(),
                edgeList.size(),
                focusId,
                neighbours);
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.GraphNeighbourhoodResponse neighbourhood(String userId, GraphNodeType type, String nodeId) {
        AnalyticsDtos.GraphNode node = resolveNode(userId, type, nodeId);
        if (node == null) {
            throw AppException.notFound("That node does not exist");
        }
        List<LifeGraphEdge> adjacent = edgeRepository.findAdjacent(userId, type, nodeId);
        List<AnalyticsDtos.GraphNode> neighbours = new ArrayList<>();
        for (LifeGraphEdge edge : adjacent) {
            GraphNodeType otherType = edge.getSourceType() == type && edge.getSourceId().equals(nodeId)
                    ? edge.getTargetType() : edge.getSourceType();
            String otherId = edge.getSourceType() == type && edge.getSourceId().equals(nodeId)
                    ? edge.getTargetId() : edge.getSourceId();
            AnalyticsDtos.GraphNode resolved = resolveNode(userId, otherType, otherId);
            if (resolved != null) {
                neighbours.add(resolved);
            }
        }
        List<AnalyticsDtos.GraphEdge> edgeList = adjacent.stream()
                .map(edge -> new AnalyticsDtos.GraphEdge(edge.getId(),
                        edge.getSourceType().name() + ":" + edge.getSourceId(),
                        edge.getTargetType().name() + ":" + edge.getTargetId(),
                        edge.getRelation().name(), edge.getWeight()))
                .toList();
        return new AnalyticsDtos.GraphNeighbourhoodResponse(node, edgeList, neighbours, shortestPathToGoal(userId, type, nodeId));
    }

    /** Breadth-first traversal towards the nearest goal, giving "how does this help my goals?" */
    @Transactional(readOnly = true)
    public List<String> shortestPathToGoal(String userId, GraphNodeType startType, String startId) {
        Map<String, List<LifeGraphEdge>> adjacency = new HashMap<>();
        edgeRepository.findByUserIdAndSourceTypeIn(userId, List.of(GraphNodeType.values())).stream()
                .filter(edge -> edge.getRelation() == GraphRelation.CONTRIBUTES_TO
                        || edge.getRelation() == GraphRelation.SUPPORTS
                        || edge.getRelation() == GraphRelation.RELATED_TO
                        || edge.getRelation() == GraphRelation.DERIVED_FROM)
                .forEach(edge -> {
                    adjacency.computeIfAbsent(nodeKey(edge.getSourceType(), edge.getSourceId()), ignored -> new ArrayList<>())
                            .add(edge);
                });

        String start = nodeKey(startType, startId);
        Deque<String> queue = new ArrayDeque<>();
        Map<String, String> previous = new HashMap<>();
        Set<String> visited = new HashSet<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.startsWith(GraphNodeType.GOAL.name() + ":")) {
                return buildPath(previous, current, start);
            }
            for (LifeGraphEdge edge : adjacency.getOrDefault(current, List.of())) {
                String next = nodeKey(edge.getTargetType(), edge.getTargetId());
                if (visited.add(next)) {
                    previous.put(next, current);
                    queue.add(next);
                }
            }
            if (visited.size() > 2000) {
                break;
            }
        }
        return List.of();
    }

    private List<String> buildPath(Map<String, String> previous, String current, String start) {
        LinkedHashSet<String> path = new LinkedHashSet<>();
        String cursor = current;
        int guard = 0;
        while (cursor != null && guard++ < 50) {
            path.addFirst(describe(cursor));
            if (cursor.equals(start)) {
                break;
            }
            cursor = previous.get(cursor);
        }
        return List.copyOf(path);
    }

    private String describe(String nodeKey) {
        int index = nodeKey.indexOf(':');
        return nodeKey.substring(index + 1);
    }

    @Transactional(readOnly = true)
    public List<AnalyticsDtos.GraphNode> neighbours(String userId, GraphNodeType type, String nodeId) {
        List<AnalyticsDtos.GraphNode> result = new ArrayList<>();
        for (LifeGraphEdge edge : edgeRepository.findAdjacent(userId, type, nodeId)) {
            boolean outgoing = edge.getSourceType() == type && edge.getSourceId().equals(nodeId);
            GraphNodeType otherType = outgoing ? edge.getTargetType() : edge.getSourceType();
            String otherId = outgoing ? edge.getTargetId() : edge.getSourceId();
            AnalyticsDtos.GraphNode node = resolveNode(userId, otherType, otherId);
            if (node != null) {
                result.add(node);
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.GraphNode resolveNode(String userId, GraphNodeType type, String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return switch (type) {
            case GOAL -> goalRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                    .map(goal -> new AnalyticsDtos.GraphNode(goal.getId(), type.name(), goal.getTitle(),
                            goal.getCategory(), goal.getStatus().name(), goal.getColor(), goal.getProgress(),
                            Map.of("progress", goal.getProgress(), "priority", goal.getPriority().name(),
                                    "targetDate", String.valueOf(goal.getTargetDate()))))
                    .orElse(null);
            case TASK -> taskRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                    .map(task -> new AnalyticsDtos.GraphNode(task.getId(), type.name(), task.getTitle(),
                            task.getCategory(), task.getStatus().name(), null,
                            task.getStatus() == TaskStatus.COMPLETED ? 0 : 1,
                            Map.of("priority", task.getPriority().name(),
                                    "deadline", String.valueOf(task.getDeadline()),
                                    "estimatedMinutes", String.valueOf(task.getEstimatedMinutes()))))
                    .orElse(null);
            case HABIT -> habitRepository.findByIdAndUserId(id, userId)
                    .map(habit -> new AnalyticsDtos.GraphNode(habit.getId(), type.name(), habit.getName(),
                            habit.getCategory(), habit.isArchived() ? "ARCHIVED" : "ACTIVE", habit.getColor(), 1,
                            Map.of("frequency", habit.getFrequencyType().name())))
                    .orElse(null);
            case SKILL -> skillRepository.findByIdAndUserId(id, userId)
                    .map(skill -> new AnalyticsDtos.GraphNode(skill.getId(), type.name(), skill.getName(),
                            skill.getCategory(), "ACTIVE", null, skill.getProficiency(),
                            Map.of("proficiency", String.valueOf(skill.getProficiency()))))
                    .orElse(null);
            case PROJECT -> projectRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                    .map(project -> new AnalyticsDtos.GraphNode(project.getId(), type.name(), project.getName(),
                            project.getStatus().name(), project.getStatus().name(), project.getColor(), 1, Map.of()))
                    .orElse(null);
            case LEARNING_GOAL -> learningGoalRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                    .map(goal -> new AnalyticsDtos.GraphNode(goal.getId(), type.name(), goal.getTitle(),
                            goal.getCategory(), goal.getStatus().name(), null, goal.getProgress(),
                            Map.of("progress", goal.getProgress(), "hoursSpent", String.valueOf(goal.getHoursSpent()))))
                    .orElse(null);
            case LEARNING_TOPIC -> learningTopicRepository.findByIdAndUserId(id, userId)
                    .map(topic -> new AnalyticsDtos.GraphNode(topic.getId(), type.name(), topic.getTitle(),
                            null, topic.isCompleted() ? "COMPLETED" : "ACTIVE", null, topic.isCompleted() ? 0 : 1,
                            Map.of()))
                    .orElse(null);
            case CALENDAR_EVENT -> calendarEventRepository.findByIdAndUserId(id, userId)
                    .map(event -> new AnalyticsDtos.GraphNode(event.getId(), type.name(), event.getTitle(),
                            event.getEventType().name(), event.getEventType().name(), event.getColor(), 1,
                            Map.of("startAt", String.valueOf(event.getStartAt()))))
                    .orElse(null);
            case FOCUS_SESSION -> focusSessionRepository.findByIdAndUserId(id, userId)
                    .map(session -> new AnalyticsDtos.GraphNode(session.getId(), type.name(),
                            session.getMode().name() + " session", null,
                            session.isCompleted() ? "COMPLETED" : "OPEN", null,
                            Math.max(1, session.getActualMinutes() / 25),
                            Map.of("minutes", String.valueOf(session.getActualMinutes()))))
                    .orElse(null);
            case JOURNAL_ENTRY, KNOWLEDGE_DOCUMENT -> new AnalyticsDtos.GraphNode(id, type.name(),
                    type == GraphNodeType.JOURNAL_ENTRY ? "Journal entry" : "Knowledge document",
                    null, "ACTIVE", null, 1, Map.of("private", true));
        };
    }

    /** Creates a synthetic node so users can hand-connect entities the automatic rules cannot infer. */
    @Transactional
    public AnalyticsDtos.GraphNode createPlaceholder(String userId, AnalyticsDtos.CreateNodeRequest request) {
        AnalyticsDtos.GraphNode node = new AnalyticsDtos.GraphNode(
                "custom:" + java.util.UUID.randomUUID(), request.type().name(), request.label(),
                request.subtitle(), "ACTIVE", null, request.weight() == null ? 1 : request.weight(), Map.of());
        return node;
    }

    @Transactional
    public AnalyticsDtos.GraphEdge createEdge(String userId, AnalyticsDtos.CreateEdgeRequest request) {
        if (resolveNode(userId, request.sourceType(), request.sourceId()) == null) {
            throw AppException.badRequest("The source node does not exist");
        }
        if (resolveNode(userId, request.targetType(), request.targetId()) == null) {
            throw AppException.badRequest("The target node does not exist");
        }
        LifeGraphEdge edge = link(userId, request.sourceType(), request.sourceId(), request.targetType(),
                request.targetId(), request.relation(), request.weight() == null ? 1.0 : request.weight());
        return new AnalyticsDtos.GraphEdge(edge.getId(),
                request.sourceType().name() + ":" + request.sourceId(),
                request.targetType().name() + ":" + request.targetId(),
                edge.getRelation().name(), edge.getWeight());
    }

    @Transactional(readOnly = true)
    public long edgeCount(String userId) {
        return edgeRepository.countByUserId(userId);
    }

    private String key(GraphNodeType type, String id) {
        return type.name() + ":" + id;
    }

    private String nodeKey(GraphNodeType type, String id) {
        return type.name() + ":" + id;
    }
}