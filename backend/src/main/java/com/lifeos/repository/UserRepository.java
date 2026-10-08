package com.lifeos.repository;

import com.lifeos.entity.User;
import com.lifeos.entity.enums.Role;
import com.lifeos.entity.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByIdAndDeletedAtIsNull(String id);

    long countByStatus(UserStatus status);

    long countByCreatedAtAfter(java.time.Instant instant);

    Page<User> findByDeletedAtIsNull(Pageable pageable);

    @Query("""
            select u from User u
            where u.deletedAt is null
              and (:query is null or lower(u.fullName) like lower(concat('%', :query, '%'))
                   or lower(u.email) like lower(concat('%', :query, '%')))
            order by u.createdAt desc
            """)
    Page<User> search(@Param("query") String query, Pageable pageable);

    Page<User> findByRole(Role role, Pageable pageable);
}