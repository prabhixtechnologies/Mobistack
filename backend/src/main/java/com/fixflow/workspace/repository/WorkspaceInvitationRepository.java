package com.fixflow.workspace.repository;

import com.fixflow.workspace.domain.WorkspaceInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, UUID> {

    Optional<WorkspaceInvitation> findByTokenHash(String tokenHash);

    List<WorkspaceInvitation> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);
}
