package com.lifeos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Directed edge: {@code taskId} cannot start until {@code dependsOnTaskId} is done. */
@Entity
@Table(name = "task_dependencies")
@Getter
@Setter
public class TaskDependency extends CreatedEntity {

    @Column(name = "task_id", length = 36, nullable = false)
    private String taskId;

    @Column(name = "depends_on_task_id", length = 36, nullable = false)
    private String dependsOnTaskId;
}