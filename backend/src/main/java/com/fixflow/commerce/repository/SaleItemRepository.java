package com.fixflow.commerce.repository;

import com.fixflow.commerce.domain.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SaleItemRepository extends JpaRepository<SaleItem, UUID> {

    List<SaleItem> findBySaleIdOrderByCreatedAtAsc(UUID saleId);
}
