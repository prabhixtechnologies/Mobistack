package com.fixflow.catalog.dto;

import com.fixflow.catalog.domain.ProductCompatibility;
import com.fixflow.catalog.domain.StockStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    // ---- Category ----------------------------------------------------

    @Schema(name = "CategoryRequest")
    public record CategoryRequest(
            @NotBlank @Size(max = 48) String code,
            @NotBlank @Size(max = 80) String name,
            @Size(max = 48) String icon,
            @Size(max = 9) String color,
            Integer sortOrder,
            Boolean compatibilityRelevant,
            Boolean active
    ) {
    }

    @Schema(name = "CategoryResponse")
    public record CategoryResponse(
            UUID id,
            String code,
            String name,
            String icon,
            String color,
            int sortOrder,
            boolean compatibilityRelevant,
            boolean active
    ) {
    }

    // ---- Brand -------------------------------------------------------

    @Schema(name = "BrandRequest")
    public record BrandRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 9) String color,
            String logoUrl,
            Integer sortOrder,
            Boolean active
    ) {
    }

    @Schema(name = "BrandResponse")
    public record BrandResponse(
            UUID id,
            String name,
            String color,
            String logoUrl,
            int sortOrder,
            boolean active,
            long deviceCount
    ) {
    }

    // ---- Device ------------------------------------------------------

    @Schema(name = "DeviceModelRequest")
    public record DeviceModelRequest(
            @NotNull UUID brandId,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 60) String modelCode,
            @Size(max = 40) String variant,
            Integer releaseYear,
            @Schema(description = "Alternate names created alongside the model")
            List<String> aliases,
            Boolean active
    ) {
    }

    @Schema(name = "DeviceModelResponse")
    public record DeviceModelResponse(
            UUID id,
            String name,
            UUID brandId,
            String brandName,
            String modelCode,
            String variant,
            Integer releaseYear,
            int popularity,
            boolean active,
            List<AliasResponse> aliases,
            int compatibilityGroupCount
    ) {
    }

    @Schema(name = "ResolveDeviceRequest")
    public record ResolveDeviceRequest(
            @NotBlank @Size(max = 160) String text,
            @Size(max = 80) String defaultBrand
    ) {
    }

    @Schema(name = "AliasResponse")
    public record AliasResponse(UUID id, String alias, String source) {
    }

    @Schema(name = "AddAliasRequest")
    public record AddAliasRequest(@NotBlank @Size(max = 120) String alias) {
    }

    // ---- Compatibility group ----------------------------------------

    @Schema(name = "CompatibilityGroupRequest")
    public record CompatibilityGroupRequest(
            @Size(max = 64) String code,
            @NotBlank @Size(max = 160) String name,
            UUID categoryId,
            String notes,
            Boolean verified,
            Boolean active,
            @Schema(description = "Device models to place in the group on creation")
            List<UUID> deviceModelIds,
            @Schema(description = "Counter strings such as Samsung A32 4G; resolved on create")
            List<String> deviceTexts
    ) {
    }

    @Schema(name = "CompatibilityGroupResponse")
    public record CompatibilityGroupResponse(
            UUID id,
            String code,
            String name,
            UUID categoryId,
            String categoryName,
            String notes,
            boolean verified,
            boolean active,
            List<GroupDeviceResponse> devices,
            long linkedProductCount,
            Instant createdAt
    ) {
    }

    @Schema(name = "GroupDeviceResponse")
    public record GroupDeviceResponse(
            UUID deviceModelId,
            String deviceName,
            String brandName,
            String variant,
            boolean primaryDevice
    ) {
    }

    @Schema(name = "GroupDeviceRequest")
    public record GroupDeviceRequest(
            @NotNull UUID deviceModelId,
            Boolean primaryDevice,
            @Size(max = 255) String note
    ) {
    }

    @Schema(name = "CopyGroupRequest")
    public record CopyGroupRequest(
            UUID categoryId,
            @Size(max = 160) String name
    ) {
    }

    @Schema(name = "GroupMembershipRequest")
    public record GroupMembershipRequest(
            List<UUID> deviceModelIds,
            List<String> deviceTexts
    ) {
    }

    @Schema(name = "CompatibilityOverviewResponse")
    public record CompatibilityOverviewResponse(
            List<CategoryOverview> categories,
            long totalGroups
    ) {
    }

    @Schema(name = "CategoryOverview")
    public record CategoryOverview(
            UUID id,
            String code,
            String name,
            String icon,
            String color,
            int sortOrder,
            long groupCount
    ) {
    }

    // ---- Product -----------------------------------------------------

    @Schema(name = "ProductRequest")
    public record ProductRequest(
            @NotNull UUID categoryId,
            UUID brandId,
            @NotBlank @Size(max = 200) String name,
            String description,
            @Size(max = 16) String hsnCode,
            @Size(max = 16) String unit,
            @DecimalMin("0.0") BigDecimal taxRate,
            String imageUrl,
            Boolean active
    ) {
    }

    @Schema(name = "ProductResponse")
    public record ProductResponse(
            UUID id,
            UUID categoryId,
            String categoryName,
            UUID brandId,
            String brandName,
            String name,
            String description,
            String hsnCode,
            String unit,
            BigDecimal taxRate,
            String imageUrl,
            boolean active,
            int variantCount,
            int totalStock,
            List<ProductVariantResponse> variants,
            List<CompatibilityLinkResponse> compatibility
    ) {
    }

    // ---- Variant -----------------------------------------------------

    @Schema(name = "ProductVariantRequest")
    public record ProductVariantRequest(
            @Size(max = 64) String sku,
            @Size(max = 64) String barcode,
            @NotBlank @Size(max = 160) String variantName,
            @Size(max = 24) String grade,
            @Size(max = 40) String quality,
            @Size(max = 40) String color,
            UUID supplierId,

            @NotNull @DecimalMin("0.0") BigDecimal costPrice,
            @NotNull @DecimalMin("0.0") BigDecimal retailPrice,
            @DecimalMin("0.0") BigDecimal wholesalePrice,
            @DecimalMin("0.0") BigDecimal repairPrice,
            @DecimalMin("0.0") BigDecimal minPrice,
            @DecimalMin("0.0") BigDecimal clearancePrice,

            @Schema(description = "Opening stock; posts an OPENING ledger entry when above zero")
            @Min(0) Integer openingStock,
            @Min(0) Integer reorderLevel,
            @Min(0) Integer maxStockLevel,
            @Min(0) Integer warrantyDays,
            @Size(max = 64) String batchNo,
            Boolean serialTracked,
            @Size(max = 64) String location,
            Boolean active
    ) {
    }

    @Schema(name = "ProductVariantResponse")
    public record ProductVariantResponse(
            UUID id,
            UUID productId,
            String productName,
            String categoryName,
            String variantName,
            String sku,
            String barcode,
            String grade,
            String quality,
            String color,
            UUID supplierId,
            String supplierName,

            BigDecimal costPrice,
            BigDecimal retailPrice,
            BigDecimal wholesalePrice,
            BigDecimal repairPrice,
            BigDecimal minPrice,
            BigDecimal clearancePrice,

            int onHandQty,
            int reservedQty,
            int availableQty,
            int reorderLevel,
            StockStatus stockStatus,
            BigDecimal stockValueAtCost,

            int warrantyDays,
            String batchNo,
            boolean serialTracked,
            String location,
            Instant lastSoldAt,
            Instant lastPurchasedAt,
            boolean active,
            UUID catalogComponentId
    ) {
    }

    // ---- Product compatibility links --------------------------------

    @Schema(name = "CompatibilityLinkRequest",
            description = "Link a product to a compatibility group, or to one specific model")
    public record CompatibilityLinkRequest(
            UUID compatibilityGroupId,
            UUID deviceModelId,
            ProductCompatibility.FitQuality fitQuality,
            @Size(max = 255) String note
    ) {
    }

    @Schema(name = "CompatibilityLinkResponse")
    public record CompatibilityLinkResponse(
            UUID id,
            UUID compatibilityGroupId,
            String compatibilityGroupName,
            UUID deviceModelId,
            String deviceModelName,
            ProductCompatibility.FitQuality fitQuality,
            String note,
            int deviceCount
    ) {
    }
}
