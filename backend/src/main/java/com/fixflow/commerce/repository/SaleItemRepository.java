package com.fixflow.commerce.repository;

import com.fixflow.commerce.domain.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SaleItemRepository extends JpaRepository<SaleItem, UUID> {

    List<SaleItem> findBySaleIdOrderByCreatedAtAsc(UUID saleId);

    /** Lines for a whole page of sales, so listing does not query per sale. */
    List<SaleItem> findBySaleIdInOrderByCreatedAtAsc(Collection<UUID> saleIds);
}
