package com.fixflow.catalog.dto;

import com.fixflow.catalog.domain.StockStatus;
import com.fixflow.pricing.domain.PricingFlag;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Shapes for the flagship workflow: search a device, see every compatible part
 * grouped by category with live stock and price, then sell or consume it.
 */
public final class CompatibilityDtos {

    private CompatibilityDtos() {
    }

    @Schema(name = "DeviceSummary")
    public record DeviceSummary(
            UUID id,
            String name,
            UUID brandId,
            String brandName,
            String modelCode,
            List<String> aliases
    ) {
    }

    @Schema(name = "CompatibilityGroupSummary")
    public record CompatibilityGroupSummary(
            UUID id,
            String code,
            String name,
            UUID categoryId,
            String categoryName,
            boolean verified,
            int deviceCount
    ) {
    }

    @Schema(name = "PartOption", description = "One buyable variant that fits the searched device")
    public record PartOption(
            UUID variantId,
            UUID productId,
            String productName,
            String variantName,
            String sku,
            String barcode,
            String grade,
            String quality,
            String color,
            int onHandQty,
            int reservedQty,
            int availableQty,
            StockStatus stockStatus,
            BigDecimal price,
            BigDecimal costPrice,
            BigDecimal marginAmount,
            String priceSource,
            Integer warrantyDays,
            String location
    ) {
    }

    @Schema(name = "CategoryPartsSummary",
            description = "The 'Display — 2 in stock — 2,800 to 4,500' row on the device screen")
    public record CategoryPartsSummary(
            UUID categoryId,
            String categoryCode,
            String categoryName,
            String icon,
            String color,
            int variantCount,
            int totalAvailable,
            StockStatus stockStatus,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            List<PartOption> options
    ) {
    }

    @Schema(name = "DeviceCompatibilityView",
            description = "Everything the shopkeeper needs after searching one phone")
    public record DeviceCompatibilityView(
            DeviceSummary device,
            List<DeviceSummary> compatibleModels,
            List<CompatibilityGroupSummary> groups,
            List<CategoryPartsSummary> categories,
            int totalPartsAvailable,
            int categoriesInStock,
            int categoriesOutOfStock,
            PricingFlag pricingFlag
    ) {
    }

    @Schema(name = "DeviceSearchHit")
    public record DeviceSearchHit(
            UUID id,
            String name,
            UUID brandId,
            String brandName,
            String modelCode,
            List<String> matchedAliases,
            int partsInStock,
            double score
    ) {
    }
}
