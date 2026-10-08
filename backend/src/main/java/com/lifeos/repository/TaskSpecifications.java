package com.lifeos.repository;

import com.lifeos.entity.Task;
import com.lifeos.entity.enums.Priority;
import com.lifeos.entity.enums.TaskStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Composable task predicates. Every specification is always AND-ed with the owning user id,
 * which is what prevents a caller from ever reading another account's tasks.
 */
public final class TaskSpecifications {

    private TaskSpecifications() {
    }

    public static Specification<Task> ownedBy(String userId) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("userId"), userId),
                cb.isNull(root.get("deletedAt")));
    }

    public static Specification<Task> withStatuses(Collection<TaskStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    public static Specification<Task> withPriorities(Collection<Priority> priorities) {
        if (priorities == null || priorities.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("priority").in(priorities);
    }

    public static Specification<Task> withCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("category"), category.trim());
    }

    public static Specification<Task> withGoalId(String goalId) {
        if (goalId == null || goalId.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("goalId"), goalId);
    }

    public static Specification<Task> withProjectId(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("projectId"), projectId);
    }

    public static Specification<Task> withMilestoneId(String milestoneId) {
        if (milestoneId == null || milestoneId.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("milestoneId"), milestoneId);
    }

    public static Specification<Task> withTag(String tag) {
        return containingTag(tag);
    }

    /** Tag membership uses a normalised CSV column, so the search must inspect the whole column. */
    public static Specification<Task> containingTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        String needle = tag.trim().toLowerCase();
        return (root, query, cb) -> cb.like(dbNormaliseTags(cb, root.get("tags")), "%," + needle + ",%");
    }

    private static Expression<String> dbNormaliseTags(CriteriaBuilder cb, Path<String> path) {
        return cb.<String>concat(cb.concat(cb.literal(","), cb.lower(path)), cb.literal(","));
    }

    public static Specification<Task> matchingText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String pattern = "%" + text.trim().toLowerCase() + "%";
        return (root, query, cb) -> {
            List<Predicate> predicates = List.of(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.get("notes"), "")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.get("tags"), "")), pattern)
            );
            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Task> withDeadlineRange(Instant from, Instant to) {
        if (from == null && to == null) {
            return null;
        }
        return (root, query, cb) -> {
            if (from != null && to != null) {
                return cb.between(root.get("deadline"), from, to);
            }
            return from != null
                    ? cb.greaterThanOrEqualTo(root.get("deadline"), from)
                    : cb.lessThanOrEqualTo(root.get("deadline"), to);
        };
    }

    public static Specification<Task> overdueAt(Instant now) {
        if (now == null) {
            return null;
        }
        return (root, query, cb) -> cb.and(
                cb.lessThan(root.get("deadline"), now),
                root.get("status").in(TaskStatus.TODO, TaskStatus.IN_PROGRESS));
    }

    public static Specification<Task> createdBetween(Instant from, Instant to) {
        if (from == null || to == null) {
            return null;
        }
        return (root, query, cb) -> cb.between(root.get("createdAt"), from, to);
    }

    public static Specification<Task> all() {
        return (root, query, cb) -> cb.conjunction();
    }

    /**
     * Applies a portable null-last ordering for {@code deadline}. The flag is built with
     * {@link CriteriaBuilder#selectCase()} instead of raw SQL so the same expression is generated for
     * H2 and MySQL. Spring Data's {@code JpaSort.unsafe} is deliberately not used: the specification
     * query path resolves every sort property as an entity attribute and rejects a SQL fragment.
     */
    public static Specification<Task> orderByDeadlineNullsLast() {
        return (root, query, cb) -> {
            if (query != null) {
                Expression<Integer> nullFlag = cb.<Integer>selectCase()
                        .when(cb.isNull(root.get("deadline")), cb.literal(1))
                        .otherwise(cb.literal(0));
                query.orderBy(cb.asc(nullFlag), cb.asc(root.get("deadline")));
            }
            return cb.conjunction();
        };
    }

    /** Highest priority first, with the same deadline tiebreaker used by the deadline ordering. */
    public static Specification<Task> orderByPriorityThenDeadline() {
        return (root, query, cb) -> {
            if (query != null) {
                Expression<Integer> nullFlag = cb.<Integer>selectCase()
                        .when(cb.isNull(root.get("deadline")), cb.literal(1))
                        .otherwise(cb.literal(0));
                query.orderBy(cb.desc(root.get("priority")), cb.asc(nullFlag), cb.asc(root.get("deadline")));
            }
            return cb.conjunction();
        };
    }
}