package com.lifeos.repository;

import com.lifeos.entity.TaskDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskDependencyRepository extends JpaRepository<TaskDependency, String> {

    List<TaskDependency> findByTaskId(String taskId);

    List<TaskDependency> findByTaskIdIn(List<String> taskIds);

    @Query("select d.dependsOnTaskId from TaskDependency d where d.taskId = :taskId")
    List<String> findDependsOnIds(@Param("taskId") String taskId);

    @Query("select d.taskId from TaskDependency d where d.dependsOnTaskId = :taskId")
    List<String> findDependentIds(@Param("taskId") String taskId);

    void deleteByTaskId(String taskId);

    long countByDependsOnTaskId(String dependsOnTaskId);
}