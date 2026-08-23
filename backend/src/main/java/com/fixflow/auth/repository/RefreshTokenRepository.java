package com.fixflow.auth.repository;

import com.fixflow.auth.domain.RefreshToken;
import com.fixflow.workspace.domain.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(UUID userId, Instant now);

    boolean existsByUserIdAndDeviceIdAndRevokedAtIsNullAndExpiresAtAfter(
            UUID userId, String deviceId, Instant now);

    boolean existsByUserIdAndRevokedAtIsNullAndExpiresAtAfter(UUID userId, Instant now);

    @Query("""
            select count(distinct t.userId) from RefreshToken t
            where t.revokedAt is null and t.expiresAt > :now
              and t.userId <> :userId
              and t.userId in (
                select m.userId from WorkspaceMembership m
                where m.workspaceId = :workspaceId and m.status = :status
              )
            """)
    long countOtherSeatedUsers(@Param("workspaceId") UUID workspaceId,
                               @Param("userId") UUID userId,
                               @Param("status") MembershipStatus status,
                               @Param("now") Instant now);

    @Query("""
            select count(distinct t.userId) from RefreshToken t
            where t.revokedAt is null and t.expiresAt > :now
              and t.userId in (
                select m.userId from WorkspaceMembership m
                where m.workspaceId = :workspaceId and m.status = :status
              )
            """)
    long countSeatedUsers(@Param("workspaceId") UUID workspaceId,
                          @Param("status") MembershipStatus status,
                          @Param("now") Instant now);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.deviceId = :deviceId and t.revokedAt is null")
    int revokeByUserAndDevice(@Param("userId") UUID userId, @Param("deviceId") String deviceId, @Param("now") Instant now);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
