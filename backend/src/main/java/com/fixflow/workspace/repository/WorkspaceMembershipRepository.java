package com.fixflow.workspace.repository;

import com.fixflow.workspace.domain.MembershipStatus;
import com.fixflow.workspace.domain.WorkspaceMembership;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceMembershipRepository extends JpaRepository<WorkspaceMembership, UUID> {

    @EntityGraph(attributePaths = {"role", "role.permissions"})
    Optional<WorkspaceMembership> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    @EntityGraph(attributePaths = {"role", "role.permissions"})
    Optional<WorkspaceMembership> findByWorkspaceIdAndUserIdAndStatus(
            UUID workspaceId, UUID userId, MembershipStatus status);

    @EntityGraph(attributePaths = {"role", "role.permissions"})
    List<WorkspaceMembership> findByUserIdOrderByLastSelectedAtDesc(UUID userId);

    @EntityGraph(attributePaths = {"role"})
    Page<WorkspaceMembership> findByWorkspaceIdAndStatus(
            UUID workspaceId, MembershipStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"role"})
    Page<WorkspaceMembership> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    @EntityGraph(attributePaths = {"role", "role.permissions"})
    List<WorkspaceMembership> findAllByWorkspaceIdAndStatus(UUID workspaceId, MembershipStatus status);

    long countByWorkspaceIdAndStatus(UUID workspaceId, MembershipStatus status);

    interface WorkspaceCount {
        UUID getWorkspaceId();
        long getTotal();
    }

    @Query("""
            select m.workspaceId as workspaceId, count(m) as total
            from WorkspaceMembership m
            where m.status = :status and m.workspaceId in :ids
            group by m.workspaceId
            """)
    List<WorkspaceCount> countByWorkspaceIdInAndStatus(
            @Param("ids") Collection<UUID> ids,
            @Param("status") MembershipStatus status);

    boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    boolean existsByUserId(UUID userId);

    @Query("""
            select m from WorkspaceMembership m
            join fetch m.role r
            left join fetch r.permissions
            where m.userId = :userId and m.status = :status
            order by m.lastSelectedAt desc nulls last, m.createdAt desc
            """)
    List<WorkspaceMembership> findActiveForUser(@Param("userId") UUID userId,
                                                @Param("status") MembershipStatus status);
}
