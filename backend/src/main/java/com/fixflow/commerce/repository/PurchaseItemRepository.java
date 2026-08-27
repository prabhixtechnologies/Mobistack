package com.fixflow.commerce.repository;

import com.fixflow.commerce.domain.PurchaseItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, UUID> {

    List<PurchaseItem> findByPurchaseIdOrderByCreatedAtAsc(UUID purchaseId);

    /** Lines for a whole page of purchases, so listing does not query per purchase. */
    List<PurchaseItem> findByPurchaseIdInOrderByCreatedAtAsc(Collection<UUID> purchaseIds);
}
