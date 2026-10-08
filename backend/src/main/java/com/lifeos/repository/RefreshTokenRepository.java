package com.lifeos.repository;

import com.lifeos.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedFalseAndUserAgentIsNotNullOrderByIssuedAtDesc(String userId);

    long countByUserIdAndRevokedFalse(String userId);

    @Modifying
    @Query("update RefreshToken t set t.revoked = true, t.revokedReason = :reason where t.userId = :userId and t.revoked = false")
    int revokeAllForUser(@Param("userId") String userId, @Param("reason") String reason);

    @Modifying
    @Query("delete from RefreshToken t where t.userId = :userId")
    int deleteAllForUser(@Param("userId") String userId);

    long deleteByExpiresAtBefore(Instant instant);
}