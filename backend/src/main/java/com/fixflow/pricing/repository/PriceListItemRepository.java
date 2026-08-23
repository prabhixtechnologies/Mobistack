package com.fixflow.pricing.repository;

import com.fixflow.pricing.domain.PriceListItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PriceListItemRepository extends JpaRepository<PriceListItem, UUID> {

    List<PriceListItem> findByPriceListId(UUID priceListId);

    /**
     * Overrides that could apply to this variant, best quantity tier first.
     */
    @Query("""
            select i from PriceListItem i
            where i.productVariantId = :variantId and i.priceListId in :priceListIds
              and i.minQuantity <= :quantity
            order by i.minQuantity desc
            """)
    List<PriceListItem> findApplicable(@Param("variantId") UUID variantId,
                                       @Param("priceListIds") Collection<UUID> priceListIds,
                                       @Param("quantity") int quantity);
}
