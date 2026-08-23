package com.fixflow.inventory.repository;

import com.fixflow.inventory.domain.StockAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockAlertRepository extends JpaRepository<StockAlert, UUID> {

    @Query("""
            select a from StockAlert a
            where a.productVariantId = :variantId and a.alertType = :type and a.status <> 'RESOLVED'
            """)
    Optional<StockAlert> findOpen(@Param("variantId") UUID variantId,
                                  @Param("type") StockAlert.AlertType type);

    @Query("""
            select a from StockAlert a
            where a.shopId = :shopId and a.status <> 'RESOLVED'
            order by case a.severity when 'RED' then 0 when 'ORANGE' then 1 else 2 end, a.createdAt desc
            """)
    Page<StockAlert> findOpenForShop(@Param("shopId") UUID shopId, Pageable pageable);

    @Query("""
            select a from StockAlert a
            where a.shopId = :shopId and a.status <> 'RESOLVED'
            order by case a.severity when 'RED' then 0 when 'ORANGE' then 1 else 2 end, a.createdAt desc
            """)
    List<StockAlert> findOpenForShop(@Param("shopId") UUID shopId);

    @Query("select count(a) from StockAlert a where a.shopId = :shopId and a.status <> 'RESOLVED'")
    long countOpen(@Param("shopId") UUID shopId);
}
