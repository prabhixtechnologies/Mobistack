package com.fixflow.group.repository;

import com.fixflow.group.domain.GroupJoinRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupJoinRequestRepository extends JpaRepository<GroupJoinRequest, UUID> {

    Optional<GroupJoinRequest> findFirstByGroupIdAndWorkspaceIdAndStatus(UUID groupId, UUID workspaceId, String status);

    Optional<GroupJoinRequest> findFirstByWorkspaceIdAndStatusOrderByCreatedAtDesc(UUID workspaceId, String status);

    List<GroupJoinRequest> findByGroupIdAndStatusOrderByCreatedAtAsc(UUID groupId, String status);
}
