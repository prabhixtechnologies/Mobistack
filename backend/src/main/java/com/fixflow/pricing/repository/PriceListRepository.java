package com.fixflow.pricing.repository;

import com.fixflow.pricing.domain.PriceList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PriceListRepository extends JpaRepository<PriceList, UUID> {

    Optional<PriceList> findByIdAndShopId(UUID id, UUID shopId);

    Optional<PriceList> findByShopIdAndCode(UUID shopId, String code);

    List<PriceList> findByShopIdOrderByPriorityAscNameAsc(UUID shopId);

    @Query("""
            select l from PriceList l
            where l.shopId = :shopId and l.active
              and (l.validFrom is null or l.validFrom <= :now)
              and (l.validTo is null or l.validTo >= :now)
            order by l.priority asc
            """)
    List<PriceList> findActive(@Param("shopId") UUID shopId, @Param("now") Instant now);
}
