package com.lifeos.service;

import com.lifeos.entity.enums.Priority;
import com.lifeos.entity.enums.TaskStatus;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;

/** Filter, sort and paging options accepted by the task search endpoint. */
public record TaskQuery(
        String text,
        Collection<TaskStatus> statuses,
        Collection<Priority> priorities,
        String category,
        String goalId,
        String projectId,
        String milestoneId,
        String tag,
        LocalDate deadlineFrom,
        LocalDate deadlineTo,
        Boolean overdueOnly,
        String sort,
        Integer page,
        Integer size,
        String zoneId
) {
    public boolean isOverdueOnly() {
        return Boolean.TRUE.equals(overdueOnly);
    }

    public String zoneIdOrDefault() {
        return zoneId == null || zoneId.isBlank() ? ZoneId.systemDefault().getId() : zoneId;
    }
}