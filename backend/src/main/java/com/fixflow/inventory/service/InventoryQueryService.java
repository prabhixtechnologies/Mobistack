package com.fixflow.inventory.service;

import com.fixflow.catalog.domain.ProductVariant;
import com.fixflow.catalog.repository.ProductRepository;
import com.fixflow.catalog.repository.ProductVariantRepository;
import com.fixflow.inventory.domain.InventoryTransaction;
import com.fixflow.inventory.domain.InventoryTransactionType;
import com.fixflow.inventory.domain.StockAlert;
import com.fixflow.inventory.dto.InventoryDtos.InventorySnapshot;
import com.fixflow.inventory.dto.InventoryDtos.InventoryTransactionResponse;
import com.fixflow.inventory.dto.InventoryDtos.StockAlertResponse;
import com.fixflow.inventory.repository.InventoryTransactionRepository;
import com.fixflow.inventory.repository.StockAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryQueryService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final StockAlertRepository stockAlertRepository;

    @Transactional(readOnly = true)
    public InventorySnapshot snapshot(UUID shopId) {
        return new InventorySnapshot(
                productRepository.countByShopIdAndActiveTrue(shopId),
                variantRepository.countByShopIdAndActiveTrue(shopId),
                variantRepository.sumStockUnits(shopId),
                nullToZero(variantRepository.sumStockValueAtCost(shopId)),
                nullToZero(variantRepository.sumStockValueAtRetail(shopId)),
                variantRepository.countLowStock(shopId),
                variantRepository.countOutOfStock(shopId),
                stockAlertRepository.countOpen(shopId));
    }

    @Transactional(readOnly = true)
    public Page<InventoryTransactionResponse> history(UUID shopId, UUID variantId, Pageable pageable) {
        Page<InventoryTransaction> page = variantId == null
                ? transactionRepository.findByShopIdOrderByOccurredAtDesc(shopId, pageable)
                : transactionRepository.findByProductVariantIdOrderByOccurredAtDesc(variantId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<StockAlertResponse> openAlerts(UUID shopId, Pageable pageable) {
        Page<StockAlert> page = stockAlertRepository.findOpenForShop(shopId, pageable);
        Map<UUID, ProductVariant> variants = page.getContent().stream()
                .map(StockAlert::getProductVariantId)
                .distinct()
                .map(id -> variantRepository.findByIdAndShopId(id, shopId).orElse(null))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        return page.map(alert -> {
            ProductVariant variant = variants.get(alert.getProductVariantId());
            return new StockAlertResponse(
                    alert.getId(),
                    alert.getProductVariantId(),
                    variant == null ? null : variant.getVariantName(),
                    variant == null ? null : variant.getProduct().getName(),
                    StockAlertResponse.StockAlertType.valueOf(alert.getAlertType().name()),
                    alert.getSeverity(),
                    alert.getStatus().name(),
                    alert.getObservedValue(),
                    alert.getThresholdValue(),
                    alert.getMessage(),
                    alert.getCreatedAt());
        });
    }

    public InventoryTransactionResponse toResponse(InventoryTransaction txn) {
        return new InventoryTransactionResponse(txn.getId(), txn.getProductVariantId(), txn.getType(),
                txn.getQuantity(), txn.getOnHandDelta(), txn.getReservedDelta(), txn.getBalanceAfter(),
                txn.getUnitCost(), txn.getReferenceType(), txn.getReferenceLabel(), txn.getReason(),
                txn.getOccurredAt(), txn.getCreatedByName());
    }

    public InventoryTransactionType issueType() {
        return InventoryTransactionType.OUT;
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
