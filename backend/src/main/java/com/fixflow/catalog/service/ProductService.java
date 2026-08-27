package com.fixflow.catalog.service;

import com.fixflow.audit.service.AuditAction;
import com.fixflow.audit.service.AuditService;
import com.fixflow.catalog.domain.Category;
import com.fixflow.catalog.domain.CompatibilityGroup;
import com.fixflow.catalog.domain.DeviceModel;
import com.fixflow.catalog.domain.Product;
import com.fixflow.catalog.domain.ProductCompatibility;
import com.fixflow.catalog.domain.ProductVariant;
import com.fixflow.catalog.dto.CatalogDtos.CompatibilityLinkRequest;
import com.fixflow.catalog.dto.CatalogDtos.CompatibilityLinkResponse;
import com.fixflow.catalog.dto.CatalogDtos.ProductRequest;
import com.fixflow.catalog.dto.CatalogDtos.ProductResponse;
import com.fixflow.catalog.dto.CatalogDtos.ProductVariantRequest;
import com.fixflow.catalog.dto.CatalogDtos.ProductVariantResponse;
import com.fixflow.catalog.repository.BrandRepository;
import com.fixflow.catalog.repository.CategoryRepository;
import com.fixflow.catalog.repository.CompatibilityGroupDeviceRepository;
import com.fixflow.catalog.repository.CompatibilityGroupRepository;
import com.fixflow.catalog.repository.DeviceModelRepository;
import com.fixflow.catalog.repository.ProductCompatibilityRepository;
import com.fixflow.catalog.repository.ProductRepository;
import com.fixflow.catalog.repository.ProductVariantRepository;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.util.TextNormalizer;
import com.fixflow.inventory.domain.InventoryReferenceType;
import com.fixflow.inventory.domain.InventoryTransactionType;
import com.fixflow.inventory.service.InventoryService;
import com.fixflow.inventory.service.StockMovement;
import com.fixflow.party.domain.Supplier;
import com.fixflow.party.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductCompatibilityRepository compatibilityRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final SupplierRepository supplierRepository;
    private final CompatibilityGroupRepository groupRepository;
    private final CompatibilityGroupDeviceRepository groupDeviceRepository;
    private final DeviceModelRepository deviceModelRepository;
    private final InventoryService inventoryService;
    private final CatalogMapper mapper;
    private final AuditService auditService;

    // ---- Products ----------------------------------------------------

    @Transactional(readOnly = true)
    public Page<ProductResponse> list(UUID shopId, String query, UUID categoryId, UUID brandId,
                                      boolean activeOnly, Pageable pageable) {
        String normalized = TextNormalizer.normalizeOrNull(query);
        Page<Product> page = productRepository.search(shopId, normalized == null ? "" : normalized,
                categoryId, brandId, activeOnly, pageable);
        Map<UUID, String> categoryNames = categoryNames(shopId);
        Map<UUID, String> brandNames = brandNames(shopId);
        Map<UUID, String> supplierNames = supplierNames(shopId);
        return page.map(product -> toResponse(shopId, product, categoryNames, brandNames, supplierNames, false));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID shopId, UUID id) {
        Product product = require(shopId, id);
        return toResponse(shopId, product, categoryNames(shopId), brandNames(shopId),
                supplierNames(shopId), true);
    }

    @Transactional
    public ProductResponse create(UUID shopId, ProductRequest request) {
        categoryRepository.findByIdAndShopId(request.categoryId(), shopId)
                .orElseThrow(() -> ApiException.notFound("Category", request.categoryId()));

        Product product = new Product();
        product.setShopId(shopId);
        apply(shopId, product, request);
        productRepository.save(product);

        auditService.record(AuditAction.PRODUCT_CREATED, "Product", product.getId(),
                "Created product \"%s\"".formatted(product.getName()));
        return get(shopId, product.getId());
    }

    @Transactional
    public ProductResponse update(UUID shopId, UUID id, ProductRequest request) {
        Product product = require(shopId, id);
        apply(shopId, product, request);
        productRepository.save(product);

        auditService.record(AuditAction.PRODUCT_UPDATED, "Product", id,
                "Updated product \"%s\"".formatted(product.getName()));
        return get(shopId, id);
    }

    // ---- Variants ----------------------------------------------------

    @Transactional
    public ProductVariantResponse addVariant(UUID shopId, UUID productId, ProductVariantRequest request) {
        Product product = require(shopId, productId);

        ProductVariant variant = new ProductVariant();
        variant.setShopId(shopId);
        variant.setProduct(product);
        variant.setSku(resolveSku(shopId, product, request));
        applyVariant(shopId, variant, request);
        variantRepository.save(variant);

        // Opening stock goes through the ledger so the movement history starts
        // complete rather than with an unexplained balance.
        int opening = request.openingStock() == null ? 0 : request.openingStock();
        if (opening > 0) {
            inventoryService.post(shopId, StockMovement
                    .of(variant.getId(), InventoryTransactionType.OPENING, opening)
                    .unitCost(request.costPrice())
                    .reference(InventoryReferenceType.MANUAL, null, "Opening stock")
                    .reason("Opening stock")
                    .build());
        }

        auditService.record(AuditAction.VARIANT_CREATED, "ProductVariant", variant.getId(),
                "Added variant \"%s\" (%s) to %s".formatted(variant.getVariantName(), variant.getSku(),
                        product.getName()));

        return getVariant(shopId, variant.getId());
    }

    @Transactional
    public ProductVariantResponse updateVariant(UUID shopId, UUID variantId, ProductVariantRequest request) {
        ProductVariant variant = variantRepository.findByIdAndShopId(variantId, shopId)
                .orElseThrow(() -> ApiException.notFound("Product variant", variantId));

        BigDecimal previousRetail = variant.getRetailPrice();
        applyVariant(shopId, variant, request);
        variantRepository.save(variant);

        // Price changes are the single most audited action a shop cares about.
        if (previousRetail.compareTo(variant.getRetailPrice()) != 0) {
            auditService.record(AuditAction.PRICE_CHANGED, "ProductVariant", variantId,
                    "%s price changed from %s to %s".formatted(variant.getVariantName(),
                            previousRetail.toPlainString(), variant.getRetailPrice().toPlainString()),
                    Map.of("retailPrice", previousRetail),
                    Map.of("retailPrice", variant.getRetailPrice()));
        } else {
            auditService.record(AuditAction.VARIANT_UPDATED, "ProductVariant", variantId,
                    "Updated variant \"%s\"".formatted(variant.getVariantName()));
        }

        return getVariant(shopId, variantId);
    }

    @Transactional(readOnly = true)
    public ProductVariantResponse getVariant(UUID shopId, UUID variantId) {
        ProductVariant variant = variantRepository.findByIdAndShopId(variantId, shopId)
                .orElseThrow(() -> ApiException.notFound("Product variant", variantId));
        return mapper.toResponse(variant, categoryNames(shopId), supplierNames(shopId));
    }

    @Transactional(readOnly = true)
    public Page<ProductVariantResponse> searchVariants(UUID shopId, String query, UUID categoryId,
                                                       UUID brandId, UUID supplierId, boolean lowStockOnly,
                                                       boolean inStockOnly, Pageable pageable) {
        String raw = query == null ? "" : query.trim();
        String normalized = TextNormalizer.normalizeOrNull(query);
        Page<ProductVariant> page = variantRepository.search(shopId,
                normalized == null ? "" : normalized, raw,
                categoryId, brandId, supplierId, true, lowStockOnly, inStockOnly, pageable);

        Map<UUID, String> categoryNames = categoryNames(shopId);
        Map<UUID, String> supplierNames = supplierNames(shopId);
        return page.map(variant -> mapper.toResponse(variant, categoryNames, supplierNames));
    }

    // ---- Compatibility links ----------------------------------------

    @Transactional
    public List<CompatibilityLinkResponse> addCompatibility(UUID shopId, UUID productId,
                                                            CompatibilityLinkRequest request) {
        Product product = require(shopId, productId);

        boolean hasGroup = request.compatibilityGroupId() != null;
        boolean hasDevice = request.deviceModelId() != null;
        if (hasGroup == hasDevice) {
            throw ApiException.businessRule(
                    "Provide exactly one of compatibilityGroupId or deviceModelId.");
        }

        if (hasGroup) {
            groupRepository.findByIdAndShopId(request.compatibilityGroupId(), shopId)
                    .orElseThrow(() -> ApiException.notFound("Compatibility group",
                            request.compatibilityGroupId()));
            if (compatibilityRepository.existsByProductIdAndCompatibilityGroupId(
                    productId, request.compatibilityGroupId())) {
                throw ApiException.alreadyExists("This product is already linked to that group.");
            }
        } else {
            deviceModelRepository.findByIdAndShopId(request.deviceModelId(), shopId)
                    .orElseThrow(() -> ApiException.notFound("Device model", request.deviceModelId()));
            if (compatibilityRepository.existsByProductIdAndDeviceModelId(
                    productId, request.deviceModelId())) {
                throw ApiException.alreadyExists("This product is already linked to that device.");
            }
        }

        ProductCompatibility link = new ProductCompatibility();
        link.setShopId(shopId);
        link.setProductId(productId);
        link.setCompatibilityGroupId(request.compatibilityGroupId());
        link.setDeviceModelId(request.deviceModelId());
        link.setFitQuality(request.fitQuality() == null
                ? ProductCompatibility.FitQuality.EXACT : request.fitQuality());
        link.setNote(request.note());
        compatibilityRepository.save(link);

        auditService.record(AuditAction.COMPATIBILITY_LINK_ADDED, "Product", productId,
                "Linked \"%s\" to a compatibility target".formatted(product.getName()));

        return compatibilityOf(shopId, productId);
    }

    @Transactional
    public void removeCompatibility(UUID shopId, UUID linkId) {
        ProductCompatibility link = compatibilityRepository.findByIdAndShopId(linkId, shopId)
                .orElseThrow(() -> ApiException.notFound("Compatibility link", linkId));
        compatibilityRepository.delete(link);
        auditService.record(AuditAction.COMPATIBILITY_LINK_REMOVED, "Product", link.getProductId(),
                "Removed a compatibility link");
    }

    @Transactional(readOnly = true)
    public List<CompatibilityLinkResponse> compatibilityOf(UUID shopId, UUID productId) {
        List<ProductCompatibility> links = compatibilityRepository.findByShopIdAndProductId(shopId, productId);
        if (links.isEmpty()) {
            return List.of();
        }

        Map<UUID, CompatibilityGroup> groups = groupRepository.findByShopIdAndIdIn(shopId,
                        distinct(links.stream().map(ProductCompatibility::getCompatibilityGroupId))).stream()
                .collect(Collectors.toMap(CompatibilityGroup::getId, Function.identity()));

        Map<UUID, DeviceModel> devices = deviceModelRepository.findByShopIdAndIdIn(shopId,
                        distinct(links.stream().map(ProductCompatibility::getDeviceModelId))).stream()
                .collect(Collectors.toMap(DeviceModel::getId, Function.identity()));

        Map<UUID, Long> groupSizes = groupDeviceRepository.findByCompatibilityGroupIdIn(groups.keySet()).stream()
                .collect(Collectors.groupingBy(
                        com.fixflow.catalog.domain.CompatibilityGroupDevice::getCompatibilityGroupId,
                        Collectors.counting()));

        return links.stream().map(link -> {
            CompatibilityGroup group = link.getCompatibilityGroupId() == null ? null
                    : groups.get(link.getCompatibilityGroupId());
            DeviceModel device = link.getDeviceModelId() == null ? null
                    : devices.get(link.getDeviceModelId());
            int deviceCount = group == null ? 1
                    : groupSizes.getOrDefault(group.getId(), 0L).intValue();
            return new CompatibilityLinkResponse(link.getId(),
                    link.getCompatibilityGroupId(),
                    group == null ? null : group.getName(),
                    link.getDeviceModelId(),
                    device == null ? null : device.getName(),
                    link.getFitQuality(),
                    link.getNote(),
                    deviceCount);
        }).toList();
    }

    // -----------------------------------------------------------------

    private static List<UUID> distinct(java.util.stream.Stream<UUID> ids) {
        return ids.filter(java.util.Objects::nonNull).distinct().toList();
    }

    private void apply(UUID shopId, Product product, ProductRequest request) {
        product.setCategoryId(request.categoryId());
        if (request.brandId() != null) {
            brandRepository.findByIdAndShopId(request.brandId(), shopId)
                    .orElseThrow(() -> ApiException.notFound("Brand", request.brandId()));
        }
        product.setBrandId(request.brandId());
        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setHsnCode(request.hsnCode());
        if (request.unit() != null && !request.unit().isBlank()) {
            product.setUnit(request.unit());
        }
        if (request.taxRate() != null) {
            product.setTaxRate(request.taxRate());
        }
        product.setImageUrl(request.imageUrl());
        if (request.active() != null) {
            product.setActive(request.active());
        }
    }

    private void applyVariant(UUID shopId, ProductVariant variant, ProductVariantRequest request) {
        if (request.barcode() != null && !request.barcode().isBlank()) {
            variantRepository.findByShopIdAndBarcode(shopId, request.barcode())
                    .filter(other -> !other.getId().equals(variant.getId()))
                    .ifPresent(other -> {
                        throw ApiException.alreadyExists(
                                "Barcode %s is already used by %s."
                                        .formatted(request.barcode(), other.getVariantName()));
                    });
            variant.setBarcode(request.barcode().trim());
        } else {
            variant.setBarcode(null);
        }

        if (request.supplierId() != null) {
            supplierRepository.findByIdAndShopId(request.supplierId(), shopId)
                    .orElseThrow(() -> ApiException.notFound("Supplier", request.supplierId()));
        }
        variant.setSupplierId(request.supplierId());

        variant.setVariantName(request.variantName().trim());
        variant.setGrade(request.grade());
        variant.setQuality(request.quality());
        variant.setColor(request.color());

        variant.setCostPrice(request.costPrice());
        variant.setRetailPrice(request.retailPrice());
        variant.setWholesalePrice(request.wholesalePrice());
        variant.setRepairPrice(request.repairPrice());
        variant.setMinPrice(request.minPrice());
        variant.setClearancePrice(request.clearancePrice());

        if (request.reorderLevel() != null) {
            variant.setReorderLevel(request.reorderLevel());
        }
        variant.setMaxStockLevel(request.maxStockLevel());
        if (request.warrantyDays() != null) {
            variant.setWarrantyDays(request.warrantyDays());
        }
        variant.setBatchNo(request.batchNo());
        if (request.serialTracked() != null) {
            variant.setSerialTracked(request.serialTracked());
        }
        variant.setLocation(request.location());
        if (request.active() != null) {
            variant.setActive(request.active());
        }
    }

    /**
     * Uses the supplied SKU, or derives a readable one such as
     * {@code DIS-IPHONE-11-DISPLAY-GX} and de-duplicates with a numeric suffix.
     */
    private String resolveSku(UUID shopId, Product product, ProductVariantRequest request) {
        if (request.sku() != null && !request.sku().isBlank()) {
            String sku = TextNormalizer.normalizeSku(request.sku());
            if (variantRepository.existsByShopIdAndSkuIgnoreCase(shopId, sku)) {
                throw ApiException.alreadyExists("SKU %s is already in use.".formatted(sku));
            }
            return sku;
        }

        String categoryPrefix = categoryRepository.findByIdAndShopId(product.getCategoryId(), shopId)
                .map(Category::getCode)
                .map(code -> code.length() > 3 ? code.substring(0, 3) : code)
                .orElse("PRT");
        String base = (categoryPrefix + "-"
                + TextNormalizer.normalize(product.getName()).replace(' ', '-') + "-"
                + TextNormalizer.normalize(request.variantName()).replace(' ', '-'))
                .toUpperCase(java.util.Locale.ROOT);
        base = base.length() > 56 ? base.substring(0, 56) : base;

        String candidate = base;
        int suffix = 2;
        while (variantRepository.existsByShopIdAndSkuIgnoreCase(shopId, candidate)) {
            candidate = "%s-%d".formatted(base, suffix++);
        }
        return candidate;
    }

    private ProductResponse toResponse(UUID shopId, Product product, Map<UUID, String> categoryNames,
                                       Map<UUID, String> brandNames, Map<UUID, String> supplierNames,
                                       boolean includeDetail) {
        List<ProductVariant> variants =
                variantRepository.findByShopIdAndProductIdOrderByVariantNameAsc(shopId, product.getId());
        List<ProductVariantResponse> variantResponses = variants.stream()
                .map(variant -> mapper.toResponse(variant, categoryNames, supplierNames))
                .toList();
        int totalStock = variants.stream().mapToInt(ProductVariant::getOnHandQty).sum();

        return new ProductResponse(product.getId(), product.getCategoryId(),
                categoryNames.get(product.getCategoryId()), product.getBrandId(),
                product.getBrandId() == null ? null : brandNames.get(product.getBrandId()),
                product.getName(), product.getDescription(), product.getHsnCode(), product.getUnit(),
                product.getTaxRate(), product.getImageUrl(), product.isActive(),
                variantResponses.size(), totalStock, variantResponses,
                includeDetail ? compatibilityOf(shopId, product.getId()) : List.of());
    }

    private Map<UUID, String> categoryNames(UUID shopId) {
        return categoryRepository.findByShopIdOrderBySortOrderAscNameAsc(shopId).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
    }

    private Map<UUID, String> brandNames(UUID shopId) {
        return brandRepository.findByShopIdOrderBySortOrderAscNameAsc(shopId).stream()
                .collect(Collectors.toMap(brand -> brand.getId(), brand -> brand.getName()));
    }

    private Map<UUID, String> supplierNames(UUID shopId) {
        return supplierRepository.findByShopIdAndActiveTrueOrderByNameAsc(shopId).stream()
                .collect(Collectors.toMap(Supplier::getId, Supplier::getName));
    }

    private Product require(UUID shopId, UUID id) {
        return productRepository.findByIdAndShopId(id, shopId)
                .orElseThrow(() -> ApiException.notFound("Product", id));
    }
}
