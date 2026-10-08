package com.lifeos.service;

import com.lifeos.dto.LearningDtos;
import com.lifeos.entity.LearningGoal;
import com.lifeos.entity.LearningResource;
import com.lifeos.entity.LearningSession;
import com.lifeos.entity.LearningTopic;
import com.lifeos.entity.Skill;
import com.lifeos.entity.enums.GraphNodeType;
import com.lifeos.entity.enums.GraphRelation;
import com.lifeos.entity.enums.LearningStatus;
import com.lifeos.entity.enums.Origin;
import com.lifeos.exception.AppException;
import com.lifeos.repository.LearningGoalRepository;
import com.lifeos.repository.LearningResourceRepository;
import com.lifeos.repository.LearningSessionRepository;
import com.lifeos.repository.LearningTopicRepository;
import com.lifeos.repository.SkillRepository;
import com.lifeos.util.DateSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Learning goals, topic trees, study sessions, resources and self-assessed skills. */
@Service
public class LearningService {

    private final LearningGoalRepository learningGoalRepository;
    private final LearningTopicRepository topicRepository;
    private final LearningSessionRepository sessionRepository;
    private final LearningResourceRepository resourceRepository;
    private final SkillRepository skillRepository;
    private final MetricsService metricsService;
    private final LifeGraphService lifeGraphService;
    private final UserZoneService userZoneService;

    public LearningService(LearningGoalRepository learningGoalRepository,
                           LearningTopicRepository topicRepository,
                           LearningSessionRepository sessionRepository,
                           LearningResourceRepository resourceRepository,
                           SkillRepository skillRepository,
                           MetricsService metricsService,
                           LifeGraphService lifeGraphService,
                           UserZoneService userZoneService) {
        this.learningGoalRepository = learningGoalRepository;
        this.topicRepository = topicRepository;
        this.sessionRepository = sessionRepository;
        this.resourceRepository = resourceRepository;
        this.skillRepository = skillRepository;
        this.metricsService = metricsService;
        this.lifeGraphService = lifeGraphService;
        this.userZoneService = userZoneService;
    }

    // ----------------------------------------------------------------- goals

    @Transactional(readOnly = true)
    public List<LearningDtos.LearningGoalResponse> goals(String userId, LearningStatus status) {
        List<LearningGoal> goals = status == null
                ? learningGoalRepository.findAllForUser(userId)
                : learningGoalRepository.findByStatus(userId, status);
        return goals.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public LearningDtos.LearningGoalResponse get(String userId, String goalId) {
        return toResponse(requireOwned(userId, goalId));
    }

    @Transactional
    public LearningDtos.LearningGoalResponse create(String userId, LearningDtos.LearningGoalRequest request) {
        LearningGoal goal = new LearningGoal();
        goal.setUserId(userId);
        apply(userId, goal, request);
        learningGoalRepository.save(goal);
        if (request.topics() != null) {
            replaceTopics(userId, goal, request.topics());
        }
        if (goal.getSkillId() != null) {
            lifeGraphService.link(userId, GraphNodeType.LEARNING_GOAL, goal.getId(), GraphNodeType.SKILL,
                    goal.getSkillId(), GraphRelation.SUPPORTS, 1.0);
        }
        return toResponse(goal);
    }

    @Transactional
    public LearningDtos.LearningGoalResponse update(String userId, String goalId, LearningDtos.LearningGoalRequest request) {
        LearningGoal goal = requireOwned(userId, goalId);
        apply(userId, goal, request);
        learningGoalRepository.save(goal);
        if (request.topics() != null) {
            replaceTopics(userId, goal, request.topics());
        }
        return toResponse(goal);
    }

    @Transactional
    public void deleteGoal(String userId, String goalId) {
        LearningGoal goal = requireOwned(userId, goalId);
        goal.setDeletedAt(Instant.now());
        goal.setStatus(LearningStatus.ARCHIVED);
        learningGoalRepository.save(goal);
        lifeGraphService.removeNode(userId, GraphNodeType.LEARNING_GOAL, goalId);
    }

    @Transactional
    public LearningDtos.LearningGoalResponse confirmRoadmap(String userId, LearningDtos.ConfirmRoadmapRequest request) {
        // estimatedHours is optional per stage; an unstated stage gets one hour spread across its topics
        // rather than taking the whole request down.
        List<LearningDtos.TopicRequest> topics = request.stages().stream()
                .flatMap(stage -> {
                    double hours = stage.estimatedHours() == null ? 1.0 : stage.estimatedHours();
                    int minutesPerTopic = (int) Math.round(hours * 60.0 / Math.max(1, stage.topics().size()));
                    return stage.topics().stream()
                            .map(topic -> new LearningDtos.TopicRequest(topic, stage.description(),
                                    minutesPerTopic, null));
                })
                .toList();
        return create(userId, new LearningDtos.LearningGoalRequest(
                request.title(), request.description(), request.category(), request.targetDate(),
                LearningStatus.ACTIVE, null, topics));
    }

    // ---------------------------------------------------------------- topics

    @Transactional
    public LearningDtos.TopicResponse addTopic(String userId, String goalId, LearningDtos.TopicRequest request) {
        LearningGoal goal = requireOwned(userId, goalId);
        LearningTopic topic = new LearningTopic();
        topic.setLearningGoalId(goalId);
        topic.setUserId(userId);
        applyTopic(topic, request);
        topic.setPosition(request.position() != null ? request.position()
                : topicRepository.findByLearningGoalIdOrderByPositionAsc(goalId).size());
        topicRepository.save(topic);
        lifeGraphService.connectLearningTopic(userId, topic.getId(), goalId);
        refreshProgress(goal);
        return toTopicResponse(topic);
    }

    @Transactional
    public LearningDtos.TopicResponse completeTopic(String userId, String topicId, boolean completed) {
        LearningTopic topic = topicRepository.findByIdAndUserId(topicId, userId)
                .orElseThrow(() -> AppException.notFound("Topic not found"));
        topic.setCompleted(completed);
        topic.setCompletedAt(completed ? Instant.now() : null);
        topicRepository.save(topic);
        learningGoalRepository.findByIdAndUserIdAndDeletedAtIsNull(topic.getLearningGoalId(), userId)
                .ifPresent(this::refreshProgress);
        return toTopicResponse(topic);
    }

    @Transactional
    public void deleteTopic(String userId, String topicId) {
        LearningTopic topic = topicRepository.findByIdAndUserId(topicId, userId)
                .orElseThrow(() -> AppException.notFound("Topic not found"));
        topicRepository.delete(topic);
        learningGoalRepository.findByIdAndUserIdAndDeletedAtIsNull(topic.getLearningGoalId(), userId)
                .ifPresent(this::refreshProgress);
    }

    // -------------------------------------------------------------- sessions

    @Transactional
    public LearningDtos.SessionResponse recordSession(String userId, LearningDtos.SessionRequest request) {
        LearningGoal goal = null;
        if (request.learningGoalId() != null && !request.learningGoalId().isBlank()) {
            goal = requireOwned(userId, request.learningGoalId());
        }
        if (request.topicId() != null && !request.topicId().isBlank()
                && topicRepository.findByIdAndUserId(request.topicId(), userId).isEmpty()) {
            throw AppException.badRequest("The referenced topic does not exist");
        }

        LearningSession session = new LearningSession();
        session.setUserId(userId);
        session.setLearningGoalId(goal == null ? null : goal.getId());
        session.setTopicId(request.topicId());
        session.setStartedAt(request.startedAt());
        session.setEndedAt(request.endedAt() != null ? request.endedAt() : request.startedAt().plus(Duration.ofMinutes(
                request.minutes() == null ? 30 : request.minutes())));
        int rawMinutes = request.minutes() != null ? request.minutes()
                : (int) Math.max(1, Duration.between(session.getStartedAt(), session.getEndedAt()).toMinutes());
        session.setMinutes(rawMinutes);
        session.setNotes(request.notes());
        session.setQuizScore(request.quizScore());
        sessionRepository.save(session);

        ZoneId zone = userZoneService.zoneOf(userId);
        metricsService.recordStudyMinutes(userId, DateSupport.toLocalDate(session.getStartedAt(), zone), rawMinutes);

        if (goal != null) {
            goal.setHoursSpent(goal.getHoursSpent().add(BigDecimal.valueOf(rawMinutes)
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP)));
            learningGoalRepository.save(goal);
            lifeGraphService.link(userId, GraphNodeType.LEARNING_GOAL, goal.getId(), GraphNodeType.GOAL,
                    findPrimaryGoalId(userId), GraphRelation.SUPPORTS, 0.6);
        }
        if (session.getTopicId() != null && goal != null) {
            lifeGraphService.connectLearningTopic(userId, session.getTopicId(), goal.getId());
        }
        return toSessionResponse(session);
    }

    @Transactional(readOnly = true)
    public List<LearningDtos.SessionResponse> sessions(String userId, Integer limit) {
        List<LearningSession> sessions = sessionRepository.findByUserIdOrderByStartedAtDesc(userId);
        if (limit != null && limit > 0 && sessions.size() > limit) {
            sessions = sessions.subList(0, limit);
        }
        return sessions.stream().map(this::toSessionResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<LearningDtos.DailyStudyPoint> dailyStudy(String userId, int days) {
        ZoneId zone = userZoneService.zoneOf(userId);
        LocalDate today = LocalDate.now(zone);
        LocalDate from = today.minusDays(Math.min(Math.max(days, 7), 365) - 1L);
        List<LearningSession> sessions = sessionRepository.findBetween(userId,
                DateSupport.startOfDay(from, zone), DateSupport.endOfDay(today, zone));
        List<LearningDtos.DailyStudyPoint> points = new java.util.ArrayList<>();
        for (LocalDate day = from; !day.isAfter(today); day = day.plusDays(1)) {
            final LocalDate target = day;
            int minutes = sessions.stream()
                    .filter(session -> DateSupport.toLocalDate(session.getStartedAt(), zone).equals(target))
                    .mapToInt(LearningSession::getMinutes).sum();
            points.add(new LearningDtos.DailyStudyPoint(target, minutes, 0));
        }
        return points;
    }

    // ------------------------------------------------------------- resources

    @Transactional
    public LearningDtos.ResourceResponse addResource(String userId, LearningDtos.ResourceRequest request) {
        if (request.learningGoalId() != null && !request.learningGoalId().isBlank()) {
            requireOwned(userId, request.learningGoalId());
        }
        LearningResource resource = new LearningResource();
        resource.setUserId(userId);
        resource.setTitle(request.title());
        if (request.url() != null && !request.url().isBlank()) {
            resource.setUrl(com.lifeos.util.UploadValidation.requireValidUrl(request.url()));
        }
        if (request.resourceType() != null) {
            resource.setResourceType(request.resourceType());
        }
        resource.setLearningGoalId(request.learningGoalId());
        resource.setCompleted(Boolean.TRUE.equals(request.completed()));
        resourceRepository.save(resource);
        return toResourceResponse(resource);
    }

    @Transactional(readOnly = true)
    public List<LearningDtos.ResourceResponse> resources(String userId, String learningGoalId) {
        List<LearningResource> resources = learningGoalId == null || learningGoalId.isBlank()
                ? resourceRepository.findByUserIdOrderByCreatedAtDesc(userId)
                : resourceRepository.findByUserIdAndLearningGoalIdOrderByCreatedAtDesc(userId, learningGoalId);
        return resources.stream().map(this::toResourceResponse).toList();
    }

    @Transactional
    public void deleteResource(String userId, String resourceId) {
        LearningResource resource = resourceRepository.findByIdAndUserId(resourceId, userId)
                .orElseThrow(() -> AppException.notFound("Resource not found"));
        resourceRepository.delete(resource);
    }

    // ---------------------------------------------------------------- skills

    @Transactional
    public LearningDtos.SkillResponse addSkill(String userId, LearningDtos.SkillRequest request) {
        Skill skill = new Skill();
        skill.setUserId(userId);
        skill.setName(request.name());
        skill.setCategory(request.category());
        skill.setProficiency(request.proficiency() == null ? 1 : request.proficiency());
        skillRepository.save(skill);
        return new LearningDtos.SkillResponse(skill.getId(), skill.getName(), skill.getCategory(), skill.getProficiency());
    }

    @Transactional(readOnly = true)
    public List<LearningDtos.SkillResponse> skills(String userId) {
        return skillRepository.findByUserIdOrderByNameAsc(userId).stream()
                .map(skill -> new LearningDtos.SkillResponse(skill.getId(), skill.getName(), skill.getCategory(),
                        skill.getProficiency()))
                .toList();
    }

    @Transactional
    public void deleteSkill(String userId, String skillId) {
        Skill skill = skillRepository.findByIdAndUserId(skillId, userId)
                .orElseThrow(() -> AppException.notFound("Skill not found"));
        skillRepository.delete(skill);
    }

    // ------------------------------------------------------------------ stats

    @Transactional(readOnly = true)
    public LearningDtos.LearningStats statistics(String userId) {
        ZoneId zone = userZoneService.zoneOf(userId);
        LocalDate today = LocalDate.now(zone);
        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        Instant from = DateSupport.startOfDay(weekStart, zone);
        Instant to = DateSupport.endOfDay(today, zone);

        List<LearningSession> weekSessions = sessionRepository.findBetween(userId, from, to);
        int weekMinutes = weekSessions.stream().mapToInt(LearningSession::getMinutes).sum();

        return new LearningDtos.LearningStats(
                learningGoalRepository.countByUserIdAndDeletedAtIsNullAndStatus(userId, LearningStatus.ACTIVE),
                Math.round(weekMinutes / 60.0 * 10) / 10.0,
                Math.round(learningGoalRepository.sumHoursSpent(userId) * 10) / 10.0,
                weekMinutes,
                weekSessions.size(),
                Math.round(sessionRepository.avgQuizScoreSince(userId, Instant.EPOCH) * 10) / 10.0,
                learningGoalRepository.findByStatus(userId, LearningStatus.ACTIVE).stream()
                        .limit(5)
                        .map(this::toResponse)
                        .toList());
    }

    // ---------------------------------------------------------------- helpers

    @Transactional(readOnly = true)
    public LearningGoal requireOwned(String userId, String goalId) {
        return learningGoalRepository.findByIdAndUserIdAndDeletedAtIsNull(goalId, userId)
                .orElseThrow(() -> AppException.notFound("Learning goal not found"));
    }

    private void apply(String userId, LearningGoal goal, LearningDtos.LearningGoalRequest request) {
        goal.setTitle(request.title().strip());
        if (request.description() != null) {
            goal.setDescription(request.description());
        }
        goal.setCategory(request.category());
        goal.setTargetDate(request.targetDate());
        if (request.status() != null) {
            goal.setStatus(request.status());
        }
        // A goal may only be pinned to one of the caller''s own skills; a blank clears the link.
        goal.setSkillId(requireOwnedSkill(userId, request.skillId()));
    }

    private String requireOwnedSkill(String userId, String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return null;
        }
        skillRepository.findByIdAndUserId(skillId, userId)
                .orElseThrow(() -> AppException.notFound("Skill not found"));
        return skillId;
    }

    private void applyTopic(LearningTopic topic, LearningDtos.TopicRequest request) {
        topic.setTitle(request.title().strip());
        if (request.description() != null) {
            topic.setDescription(request.description());
        }
        if (request.estimatedMinutes() != null) {
            topic.setEstimatedMinutes(request.estimatedMinutes());
        }
    }

    private void replaceTopics(String userId, LearningGoal goal, List<LearningDtos.TopicRequest> requests) {
        topicRepository.deleteByLearningGoalId(goal.getId());
        topicRepository.flush();
        int position = 0;
        for (LearningDtos.TopicRequest request : requests) {
            LearningTopic topic = new LearningTopic();
            topic.setLearningGoalId(goal.getId());
            topic.setUserId(userId);
            applyTopic(topic, request);
            topic.setPosition(position++);
            topicRepository.save(topic);
            lifeGraphService.connectLearningTopic(userId, topic.getId(), goal.getId());
        }
        refreshProgress(goal);
    }

    private void refreshProgress(LearningGoal goal) {
        long total = topicRepository.countByLearningGoalId(goal.getId());
        long completed = topicRepository.countByLearningGoalIdAndCompletedTrue(goal.getId());
        goal.setProgress(total == 0 ? goal.getProgress() : (int) Math.round(completed * 100.0 / total));
        learningGoalRepository.save(goal);
    }

    private String findPrimaryGoalId(String userId) {
        return learningGoalRepository.findByStatus(userId, LearningStatus.ACTIVE).stream()
                .findFirst().map(LearningGoal::getId).orElse("");
    }

    public LearningDtos.LearningGoalResponse toResponse(LearningGoal goal) {
        List<LearningTopic> topics = topicRepository.findByLearningGoalIdOrderByPositionAsc(goal.getId());
        return new LearningDtos.LearningGoalResponse(
                goal.getId(),
                goal.getTitle(),
                goal.getDescription(),
                goal.getCategory(),
                goal.getTargetDate(),
                goal.getProgress(),
                goal.getStatus(),
                goal.getHoursSpent(),
                goal.getSkillId(),
                goal.getSource(),
                goal.isAiConfirmed(),
                topics.size(),
                topics.stream().filter(LearningTopic::isCompleted).count(),
                topics.stream().map(this::toTopicResponse).toList());
    }

    private LearningDtos.TopicResponse toTopicResponse(LearningTopic topic) {
        return new LearningDtos.TopicResponse(topic.getId(), topic.getTitle(), topic.getDescription(),
                topic.getPosition(), topic.isCompleted(), topic.getCompletedAt(), topic.getEstimatedMinutes());
    }

    private LearningDtos.SessionResponse toSessionResponse(LearningSession session) {
        String goalTitle = session.getLearningGoalId() == null ? null
                : learningGoalRepository.findById(session.getLearningGoalId()).map(LearningGoal::getTitle).orElse(null);
        String topicTitle = session.getTopicId() == null ? null
                : topicRepository.findById(session.getTopicId()).map(LearningTopic::getTitle).orElse(null);
        return new LearningDtos.SessionResponse(session.getId(), session.getLearningGoalId(), goalTitle,
                session.getTopicId(), topicTitle, session.getStartedAt(), session.getEndedAt(),
                session.getMinutes(), session.getNotes(), session.getQuizScore());
    }

    private LearningDtos.ResourceResponse toResourceResponse(LearningResource resource) {
        return new LearningDtos.ResourceResponse(resource.getId(), resource.getTitle(), resource.getUrl(),
                resource.getResourceType(), resource.getLearningGoalId(), resource.isCompleted());
    }
}