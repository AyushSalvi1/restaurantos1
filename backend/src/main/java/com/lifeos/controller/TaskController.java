package com.lifeos.controller;

import com.lifeos.common.PageResponse;
import com.lifeos.dto.TaskDtos;
import com.lifeos.entity.enums.TaskStatus;
import com.lifeos.security.CurrentUser;
import com.lifeos.service.TaskQuery;
import com.lifeos.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/** Tasks, their dependencies, board layout, recurrence and bulk operations. */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public PageResponse<TaskDtos.TaskResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<TaskStatus> status,
            @RequestParam(required = false) List<String> priority,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String goalId,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String milestoneId,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deadlineFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deadlineTo,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false, defaultValue = "deadline") String sort,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "25") Integer size,
            @RequestParam(required = false) String zoneId) {
        TaskQuery query = new TaskQuery(q, status,
                priority == null ? null : priority.stream()
                        .map(value -> com.lifeos.entity.enums.Priority.valueOf(value.toUpperCase(java.util.Locale.ROOT)))
                        .toList(),
                category, goalId, projectId, milestoneId, tag, deadlineFrom, deadlineTo, overdue,
                sort, page, size, zoneId);
        return PageResponse.of(taskService.search(CurrentUser.id(), query));
    }

    @GetMapping("/board")
    public List<TaskDtos.TaskBoardColumn> board() {
        return taskService.board(CurrentUser.id());
    }

    @GetMapping("/{taskId}")
    public TaskDtos.TaskResponse get(@PathVariable String taskId) {
        return taskService.get(CurrentUser.id(), taskId);
    }

    @PostMapping
    public ResponseEntity<TaskDtos.TaskResponse> create(@Valid @RequestBody TaskDtos.TaskRequest request) {
        TaskDtos.TaskResponse created = taskService.create(CurrentUser.id(), request);
        return ResponseEntity.created(UriComponentsBuilder.fromPath("/api/tasks/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PostMapping("/quick")
    public ResponseEntity<TaskDtos.TaskResponse> quickCreate(@Valid @RequestBody TaskDtos.QuickCreateRequest request) {
        TaskDtos.TaskResponse created = taskService.create(CurrentUser.id(), TaskDtos.TaskRequest.fromQuick(request));
        return ResponseEntity.created(UriComponentsBuilder.fromPath("/api/tasks/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PutMapping("/{taskId}")
    public TaskDtos.TaskResponse update(@PathVariable String taskId,
                                        @Valid @RequestBody TaskDtos.TaskRequest request) {
        return taskService.update(CurrentUser.id(), taskId, request);
    }

    @PatchMapping("/{taskId}/status")
    public TaskDtos.TaskResponse changeStatus(@PathVariable String taskId,
                                              @Valid @RequestBody TaskDtos.StatusUpdateRequest request) {
        return taskService.changeStatus(CurrentUser.id(), taskId, request);
    }

    @PostMapping("/{taskId}/complete")
    public TaskDtos.TaskResponse complete(@PathVariable String taskId,
                                          @Valid @RequestBody TaskDtos.CompleteRequest request) {
        return taskService.complete(CurrentUser.id(), taskId, request);
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> delete(@PathVariable String taskId) {
        taskService.delete(CurrentUser.id(), taskId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk")
    public TaskDtos.BulkActionResult bulk(@Valid @RequestBody TaskDtos.BulkActionRequest request) {
        return taskService.bulk(CurrentUser.id(), request);
    }

    @PostMapping("/reorder")
    public List<TaskDtos.TaskResponse> reorder(@Valid @RequestBody TaskDtos.ReorderRequest request) {
        return taskService.reorder(CurrentUser.id(), request);
    }

    @GetMapping("/{taskId}/dependencies")
    public List<String> dependencies(@PathVariable String taskId) {
        return taskService.dependencies(CurrentUser.id(), taskId);
    }

    @PostMapping("/{taskId}/dependencies")
    public List<String> addDependency(@PathVariable String taskId,
                                      @Valid @RequestBody TaskDtos.DependencyRequest request) {
        return taskService.addDependency(CurrentUser.id(), taskId, request.taskId());
    }

    @DeleteMapping("/{taskId}/dependencies/{dependsOnTaskId}")
    public List<String> removeDependency(@PathVariable String taskId, @PathVariable String dependsOnTaskId) {
        return taskService.removeDependency(CurrentUser.id(), taskId, dependsOnTaskId);
    }

    @PostMapping("/{taskId}/recurrence/materialise")
    public ResponseEntity<MaterialiseResponse> materialiseRecurrence(@PathVariable String taskId) {
        int created = taskService.materialiseRecurrence(CurrentUser.id(), taskId);
        return ResponseEntity.status(created == 0 ? HttpStatus.NO_CONTENT : HttpStatus.OK)
                .body(new MaterialiseResponse(created));
    }

    /** Reports exactly how many occurrences were generated, so a silent no-op cannot be mistaken for success. */
    public record MaterialiseResponse(int created) {
    }
}
