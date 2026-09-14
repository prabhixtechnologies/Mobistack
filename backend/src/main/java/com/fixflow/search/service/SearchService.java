package com.fixflow.search.service;

import com.fixflow.catalog.domain.Category;
import com.fixflow.catalog.domain.DeviceAlias;
import com.fixflow.catalog.domain.ProductVariant;
import com.fixflow.catalog.dto.CompatibilityDtos.DeviceSearchHit;
import com.fixflow.catalog.repository.BrandRepository;
import com.fixflow.catalog.repository.CategoryRepository;
import com.fixflow.catalog.repository.DeviceAliasRepository;
import com.fixflow.catalog.repository.DeviceModelRepository;
import com.fixflow.catalog.repository.ProductVariantRepository;
import com.fixflow.common.util.TextNormalizer;
import com.fixflow.commons.domain.CatalogEntities.CatalogBrand;
import com.fixflow.commons.domain.CatalogEntities.CatalogComponent;
import com.fixflow.commons.domain.CatalogEntities.CatalogDevice;
import com.fixflow.commons.repository.CatalogBrandRepository;
import com.fixflow.commons.repository.CatalogComponentRepository;
import com.fixflow.commons.repository.CatalogDeviceRepository;
import com.fixflow.config.FixFlowProperties;
import com.fixflow.pricing.domain.PricingFlag;
import com.fixflow.pricing.service.PriceContext;
import com.fixflow.pricing.service.PricingService;
import com.fixflow.search.dto.SearchDtos.BrandHit;
import com.fixflow.search.dto.SearchDtos.CommonsSearchHit;
import com.fixflow.search.dto.SearchDtos.GlobalSearchResponse;
import com.fixflow.search.dto.SearchDtos.PartSearchHit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The single search box behind the Home screen.
 *
 * <p>Typing "realme 6" has to surface the model, its aliases and every part
 * that fits it; scanning a barcode has to jump straight to one variant. Both
 * go through here so the two clients behave identically.
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    private static final int DEFAULT_DEVICE_LIMIT = 8;
    private static final int DEFAULT_PART_LIMIT = 12;
    /** Below this, trigram matching returns noise rather than suggestions. */
    private static final int MIN_QUERY_LENGTH = 2;

    private final DeviceModelRepository deviceModelRepository;
    private final DeviceAliasRepository deviceAliasRepository;
    private final ProductVariantRepository variantRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final PricingService pricingService;
    private final FixFlowProperties properties;
    private final CatalogDeviceRepository catalogDevices;
    private final CatalogComponentRepository catalogComponents;
    private final CatalogBrandRepository catalogBrands;

    @Transactional(readOnly = true)
    public GlobalSearchResponse search(UUID shopId, String rawQuery, PricingFlag flag) {
        long startedAt = System.nanoTime();
        String trimmed = rawQuery == null ? "" : rawQuery.trim();

        if (trimmed.length() < MIN_QUERY_LENGTH) {
            return new GlobalSearchResponse(trimmed, List.of(), List.of(), List.of(), null, 0,
                    elapsedMillis(startedAt), List.of(), List.of());
        }

        String normalized = TextNormalizer.normalize(trimmed);
        PricingFlag effectiveFlag = flag == null ? PricingFlag.NORMAL : flag;

        PartSearchHit exactMatch = findExactScanMatch(shopId, trimmed, effectiveFlag).orElse(null);
        List<DeviceSearchHit> devices = searchDevices(shopId, normalized);
        List<PartSearchHit> parts = searchParts(shopId, normalized, trimmed, effectiveFlag);
        List<BrandHit> brands = searchBrands(shopId, normalized);
        List<CommonsSearchHit> commonsDevices = searchCommonsDevices(trimmed);
        List<CommonsSearchHit> commonsComponents = searchCommonsComponents(trimmed);

        int total = devices.size() + parts.size() + brands.size()
                + commonsDevices.size() + commonsComponents.size();
        return new GlobalSearchResponse(trimmed, devices, parts, brands, exactMatch, total,
                elapsedMillis(startedAt), commonsDevices, commonsComponents);
    }

    /**
     * A scanned barcode or a typed SKU identifies exactly one variant, so the
     * client can skip the results list entirely.
     */
    @Transactional(readOnly = true)
    public Optional<PartSearchHit> findExactScanMatch(UUID shopId, String code, PricingFlag flag) {
        return variantRepository.findByShopIdAndBarcode(shopId, code)
                .or(() -> variantRepository.findBySku(shopId, code))
                .map(variant -> toPartHit(shopId, variant, flag, categoryNames(shopId)));
    }

    // -----------------------------------------------------------------

    private List<DeviceSearchHit> searchDevices(UUID shopId, String normalizedQuery) {
        List<DeviceModelRepository.DeviceSearchRow> rows =
                deviceModelRepository.searchDevices(shopId, normalizedQuery, DEFAULT_DEVICE_LIMIT);
        if (rows.isEmpty()) {
            return List.of();
        }

        List<UUID> deviceIds = rows.stream().map(DeviceModelRepository.DeviceSearchRow::getId).toList();
        Map<UUID, List<String>> aliases = deviceAliasRepository
                .findByShopIdAndDeviceModelIdInOrderByAliasAsc(shopId, deviceIds).stream()
                .collect(Collectors.groupingBy(DeviceAlias::getDeviceModelId,
                        Collectors.mapping(DeviceAlias::getAlias, Collectors.toList())));

        return rows.stream()
                .map(row -> new DeviceSearchHit(
                        row.getId(),
                        row.getName(),
                        row.getBrandId(),
                        row.getBrandName(),
                        row.getModelCode(),
                        row.getVariant(),
                        matchingAliases(aliases.getOrDefault(row.getId(), List.of()), normalizedQuery),
                        // Filled lazily by the device screen; the list only needs identity.
                        0,
                        row.getScore()))
                .toList();
    }

    /** Only show the aliases that actually explain the hit, not the whole list. */
    private List<String> matchingAliases(List<String> aliases, String normalizedQuery) {
        List<String> matched = aliases.stream()
                .filter(alias -> TextNormalizer.normalize(alias).contains(normalizedQuery))
                .toList();
        return matched.isEmpty() ? aliases.stream().limit(3).toList() : matched;
    }

    private List<PartSearchHit> searchParts(UUID shopId, String normalizedQuery, String rawQuery,
                                            PricingFlag flag) {
        List<ProductVariant> variants = variantRepository.search(
                        shopId, normalizedQuery, rawQuery, null, null, null,
                        true, false, false, PageRequest.of(0, DEFAULT_PART_LIMIT))
                .getContent();
        if (variants.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> categoryNames = categoryNames(shopId);
        return variants.stream()
                .map(variant -> toPartHit(shopId, variant, flag, categoryNames))
                .toList();
    }

    private List<BrandHit> searchBrands(UUID shopId, String normalizedQuery) {
        return brandRepository.findByShopIdAndActiveTrueOrderBySortOrderAscNameAsc(shopId).stream()
                .filter(brand -> TextNormalizer.normalize(brand.getName()).contains(normalizedQuery))
                .limit(5)
                .map(brand -> new BrandHit(brand.getId(), brand.getName(), 0L))
                .toList();
    }

    private PartSearchHit toPartHit(UUID shopId, ProductVariant variant, PricingFlag flag,
                                    Map<UUID, String> categoryNames) {
        var quote = pricingService.quote(PriceContext.of(shopId, variant, flag));
        return new PartSearchHit(
                variant.getId(),
                variant.getProduct().getId(),
                variant.getProduct().getName(),
                variant.getVariantName(),
                variant.getSku(),
                variant.getBarcode(),
                categoryNames.get(variant.getProduct().getCategoryId()),
                variant.getGrade(),
                variant.available(),
                variant.stockStatus(properties.getInventory().getCriticalStockFactor()),
                quote.unitPrice());
    }

    private Map<UUID, String> categoryNames(UUID shopId) {
        return categoryRepository.findByShopIdOrderBySortOrderAscNameAsc(shopId).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
    }

    private List<CommonsSearchHit> searchCommonsDevices(String term) {
        List<CatalogDevice> rows = catalogDevices.search(term, PageRequest.of(0, DEFAULT_DEVICE_LIMIT))
                .getContent();
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> brandNames = catalogBrands.findAllById(rows.stream()
                        .map(CatalogDevice::getBrandId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(CatalogBrand::getId, CatalogBrand::getName));
        return rows.stream()
                .map(device -> new CommonsSearchHit(
                        device.getId(),
                        device.getName(),
                        "device",
                        device.getBrandId(),
                        brandNames.get(device.getBrandId()),
                        null,
                        device.getVariant(),
                        device.getModelCode()))
                .toList();
    }

    private List<CommonsSearchHit> searchCommonsComponents(String term) {
        return catalogComponents.search(term, PageRequest.of(0, DEFAULT_PART_LIMIT))
                .getContent()
                .stream()
                .map(component -> new CommonsSearchHit(
                        component.getId(),
                        component.getName(),
                        "component",
                        null,
                        null,
                        component.getCategoryCode(),
                        null,
                        null))
                .toList();
    }

    private static long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }
}
