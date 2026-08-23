package com.fixflow.repair.repository;

import com.fixflow.repair.domain.RepairPart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RepairPartRepository extends JpaRepository<RepairPart, UUID> {

    List<RepairPart> findByRepairIdOrderByCreatedAtAsc(UUID repairId);
}
