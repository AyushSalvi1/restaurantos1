package com.lifeos.repository;

import com.lifeos.entity.Project;
import com.lifeos.entity.enums.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, String> {

    Optional<Project> findByIdAndUserIdAndDeletedAtIsNull(String id, String userId);

    @Query("select p from Project p where p.userId = :userId and p.deletedAt is null order by p.createdAt desc")
    List<Project> findAllForUser(@Param("userId") String userId);

    Page<Project> findByUserIdAndDeletedAtIsNull(String userId, Pageable pageable);

    long countByUserIdAndDeletedAtIsNullAndStatus(String userId, ProjectStatus status);
}