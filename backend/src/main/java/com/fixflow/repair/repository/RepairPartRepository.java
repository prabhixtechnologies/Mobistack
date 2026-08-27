package com.fixflow.repair.repository;

import com.fixflow.repair.domain.RepairPart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RepairPartRepository extends JpaRepository<RepairPart, UUID> {

    List<RepairPart> findByRepairIdOrderByCreatedAtAsc(UUID repairId);

    /** Parts for a whole page of jobs, so listing does not query per job. */
    List<RepairPart> findByRepairIdInOrderByCreatedAtAsc(Collection<UUID> repairIds);
}
