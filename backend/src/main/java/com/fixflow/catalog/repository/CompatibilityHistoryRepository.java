package com.fixflow.catalog.repository;

import com.fixflow.catalog.domain.CompatibilityHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CompatibilityHistoryRepository extends JpaRepository<CompatibilityHistory, UUID> {

    List<CompatibilityHistory> findByShopIdAndGroupIdOrderByCreatedAtDesc(UUID shopId, UUID groupId);
}
