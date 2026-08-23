package com.fixflow.support.repository;

import com.fixflow.support.domain.SupportConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupportConversationRepository extends JpaRepository<SupportConversation, UUID> {

    List<SupportConversation> findByUserIdOrderByLastMessageAtDesc(UUID userId);

    Optional<SupportConversation> findByIdAndUserId(UUID id, UUID userId);

    Page<SupportConversation> findByStatusOrderByLastMessageAtDesc(String status, Pageable pageable);

    Page<SupportConversation> findAllByOrderByLastMessageAtDesc(Pageable pageable);
}
