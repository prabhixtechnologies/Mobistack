package com.fixflow.catalog.service;

import com.fixflow.catalog.domain.Brand;
import com.fixflow.catalog.domain.Category;
import com.fixflow.catalog.domain.DeviceAlias;
import com.fixflow.catalog.domain.DeviceModel;
import com.fixflow.catalog.domain.Product;
import com.fixflow.catalog.domain.ProductVariant;
import com.fixflow.catalog.dto.CatalogDtos.AliasResponse;
import com.fixflow.catalog.dto.CatalogDtos.BrandResponse;
import com.fixflow.catalog.dto.CatalogDtos.CategoryResponse;
import com.fixflow.catalog.dto.CatalogDtos.DeviceModelResponse;
import com.fixflow.catalog.dto.CatalogDtos.ProductVariantResponse;
import com.fixflow.config.FixFlowProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Entity to DTO translation.
 *
 * <p>Methods that need related names take pre-loaded lookup maps rather than
 * fetching per row, which keeps list endpoints at a fixed number of queries.
 */
@Component
@RequiredArgsConstructor
public class CatalogMapper {

    private final FixFlowProperties properties;

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getCode(), category.getName(),
                category.getIcon(), category.getColor(), category.getSortOrder(),
                category.isCompatibilityRelevant(), category.isActive());
    }

    public BrandResponse toResponse(Brand brand, long deviceCount) {
        return new BrandResponse(brand.getId(), brand.getName(), brand.getColor(), brand.getLogoUrl(),
                brand.getSortOrder(), brand.isActive(), deviceCount);
    }

    public DeviceModelResponse toResponse(DeviceModel device, List<DeviceAlias> aliases, int groupCount) {
        List<AliasResponse> aliasResponses = aliases.stream()
                .map(alias -> new AliasResponse(alias.getId(), alias.getAlias(), alias.getSource().name()))
                .toList();
        return new DeviceModelResponse(device.getId(), device.getName(), device.getBrand().getId(),
                device.getBrand().getName(), device.getModelCode(), device.getVariant(),
                device.getReleaseYear(), device.getPopularity(), device.isActive(), aliasResponses, groupCount);
    }

    public ProductVariantResponse toResponse(ProductVariant variant, Map<UUID, String> categoryNames,
                                             Map<UUID, String> supplierNames) {
        Product product = variant.getProduct();
        BigDecimal stockValue = variant.getCostPrice()
                .multiply(BigDecimal.valueOf(variant.getOnHandQty()));

        return new ProductVariantResponse(
                variant.getId(),
                product.getId(),
                product.getName(),
                categoryNames == null ? null : categoryNames.get(product.getCategoryId()),
                variant.getVariantName(),
                variant.getSku(),
                variant.getBarcode(),
                variant.getGrade(),
                variant.getQuality(),
                variant.getColor(),
                variant.getSupplierId(),
                supplierNames == null || variant.getSupplierId() == null
                        ? null : supplierNames.get(variant.getSupplierId()),
                variant.getCostPrice(),
                variant.getRetailPrice(),
                variant.getWholesalePrice(),
                variant.getRepairPrice(),
                variant.getMinPrice(),
                variant.getClearancePrice(),
                variant.getOnHandQty(),
                variant.getReservedQty(),
                variant.available(),
                variant.getReorderLevel(),
                variant.stockStatus(properties.getInventory().getCriticalStockFactor()),
                stockValue,
                variant.getWarrantyDays(),
                variant.getBatchNo(),
                variant.isSerialTracked(),
                variant.getLocation(),
                variant.getLastSoldAt(),
                variant.getLastPurchasedAt(),
                variant.isActive(),
                variant.getCatalogComponentId());
    }
}
