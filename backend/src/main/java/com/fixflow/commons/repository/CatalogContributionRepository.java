package com.fixflow.commons.repository;

import com.fixflow.commons.domain.CatalogContribution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CatalogContributionRepository extends JpaRepository<CatalogContribution, UUID> {

    /** The review queue, oldest first, so nothing sits forever behind newer submissions. */
    Page<CatalogContribution> findByStatusOrderByCreatedAtAsc(CatalogContribution.Status status,
                                                              Pageable pageable);

    Page<CatalogContribution> findBySubmittedByOrderByCreatedAtDesc(UUID submittedBy,
                                                                    Pageable pageable);

    long countByStatus(CatalogContribution.Status status);
}
