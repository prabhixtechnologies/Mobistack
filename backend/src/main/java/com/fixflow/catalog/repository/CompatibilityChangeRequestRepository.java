package com.fixflow.catalog.repository;

import com.fixflow.catalog.domain.CompatibilityChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompatibilityChangeRequestRepository extends JpaRepository<CompatibilityChangeRequest, UUID> {

    List<CompatibilityChangeRequest> findByShopIdAndStatusOrderByCreatedAtDesc(
            UUID shopId, CompatibilityChangeRequest.Status status);

    Optional<CompatibilityChangeRequest> findByIdAndShopId(UUID id, UUID shopId);
}
