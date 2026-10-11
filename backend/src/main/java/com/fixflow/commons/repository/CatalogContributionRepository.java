package com.fixflow.commons.repository;

import com.fixflow.commons.domain.CatalogContribution;
import com.fixflow.commons.domain.CatalogContribution.Kind;
import com.fixflow.commons.domain.CatalogContribution.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CatalogContributionRepository extends JpaRepository<CatalogContribution, UUID> {

    /** The review queue, oldest first, so nothing sits forever behind newer submissions. */
    Page<CatalogContribution> findByStatusOrderByCreatedAtAsc(CatalogContribution.Status status,
                                                              Pageable pageable);

    Page<CatalogContribution> findBySubmittedByOrderByCreatedAtDesc(UUID submittedBy,
                                                                    Pageable pageable);

    long countByStatus(CatalogContribution.Status status);

    /**
     * The vote this person already has on a fitment. A rejected vote does not count: they may try again.
     */
    @Query("""
            select c from CatalogContribution c
            where c.submittedBy = :authorId
              and c.targetId = :targetId
              and c.kind = :kind
              and c.status in :statuses
            order by c.createdAt
            """)
    List<CatalogContribution> findVotes(@Param("authorId") UUID authorId,
                                        @Param("targetId") UUID targetId,
                                        @Param("kind") Kind kind,
                                        @Param("statuses") Collection<Status> statuses);

    @Query("""
            select c from CatalogContribution c
            where c.submittedBy = :authorId
              and c.targetId in :targetIds
              and c.kind in :kinds
              and c.status in :statuses
            """)
    List<CatalogContribution> findVotesForTargets(@Param("authorId") UUID authorId,
                                                  @Param("targetIds") Collection<UUID> targetIds,
                                                  @Param("kinds") Collection<Kind> kinds,
                                                  @Param("statuses") Collection<Status> statuses);
}
