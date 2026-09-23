package com.fixflow.group.repository;

import com.fixflow.group.domain.SharingGroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SharingGroupMemberRepository extends JpaRepository<SharingGroupMember, UUID> {

    List<SharingGroupMember> findByGroupIdOrderByCreatedAtAsc(UUID groupId);

    Optional<SharingGroupMember> findByGroupIdAndWorkspaceId(UUID groupId, UUID workspaceId);

    Optional<SharingGroupMember> findByGroupIdAndUserId(UUID groupId, UUID userId);

    long countByGroupIdAndWorkspaceIdIsNotNull(UUID groupId);
}
