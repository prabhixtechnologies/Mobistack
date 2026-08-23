package com.fixflow.pricing.repository;

import com.fixflow.pricing.domain.PriceRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PriceRuleRepository extends JpaRepository<PriceRule, UUID> {

    Optional<PriceRule> findByIdAndShopId(UUID id, UUID shopId);

    List<PriceRule> findByShopIdOrderByPriorityAscNameAsc(UUID shopId);

    /**
     * All rules currently in force. Small per shop, so the engine loads them
     * once and filters in memory rather than building a query per quote.
     */
    @Query("""
            select r from PriceRule r
            where r.shopId = :shopId and r.active
              and (r.validFrom is null or r.validFrom <= :now)
              and (r.validTo is null or r.validTo >= :now)
            order by r.priority asc
            """)
    List<PriceRule> findActiveRules(@Param("shopId") UUID shopId, @Param("now") Instant now);
}
