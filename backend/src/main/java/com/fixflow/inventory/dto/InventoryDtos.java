package com.fixflow.inventory.dto;

import com.fixflow.catalog.domain.StockStatus;
import com.fixflow.inventory.domain.InventoryReferenceType;
import com.fixflow.inventory.domain.InventoryTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    @Schema(name = "StockReceiveRequest")
    public record StockReceiveRequest(
            @NotNull UUID variantId,
            @NotNull @Min(1) Integer quantity,
            @DecimalMin("0.0") BigDecimal unitCost,
            @Size(max = 64) String batchNo,
            @Size(max = 160) String reason
    ) {
    }

    @Schema(name = "StockIssueRequest")
    public record StockIssueRequest(
            @NotNull UUID variantId,
            @NotNull @Min(1) Integer quantity,
            @Size(max = 160) String reason
    ) {
    }

    @Schema(name = "StockAdjustRequest")
    public record StockAdjustRequest(
            @NotNull UUID variantId,
            @NotNull @Min(0) Integer countedQuantity,
            @NotNull @Size(max = 160) String reason
    ) {
    }

    @Schema(name = "StockDamageRequest")
    public record StockDamageRequest(
            @NotNull UUID variantId,
            @NotNull @Min(1) Integer quantity,
            @NotNull @Size(max = 160) String reason
    ) {
    }

    @Schema(name = "InventoryTransactionResponse")
    public record InventoryTransactionResponse(
            UUID id,
            UUID productVariantId,
            InventoryTransactionType type,
            int quantity,
            int onHandDelta,
            int reservedDelta,
            int balanceAfter,
            BigDecimal unitCost,
            InventoryReferenceType referenceType,
            String referenceLabel,
            String reason,
            Instant occurredAt,
            String createdByName
    ) {
    }

    @Schema(name = "StockAlertResponse")
    public record StockAlertResponse(
            UUID id,
            UUID productVariantId,
            String variantName,
            String productName,
            StockAlertType alertType,
            StockStatus severity,
            String status,
            Integer observedValue,
            Integer thresholdValue,
            String message,
            Instant createdAt
    ) {
        public enum StockAlertType {
            LOW_STOCK, OUT_OF_STOCK, DEAD_STOCK, OVERSTOCK
        }
    }

    @Schema(name = "InventorySnapshot")
    public record InventorySnapshot(
            long totalProducts,
            long totalVariants,
            long stockUnits,
            BigDecimal stockValueAtCost,
            BigDecimal stockValueAtRetail,
            long lowStockCount,
            long outOfStockCount,
            long openAlertCount
    ) {
    }
}
