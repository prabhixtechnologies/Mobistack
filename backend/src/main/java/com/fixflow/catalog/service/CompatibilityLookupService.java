package com.fixflow.catalog.service;

import com.fixflow.catalog.domain.Category;
import com.fixflow.catalog.domain.CompatibilityGroup;
import com.fixflow.catalog.domain.DeviceAlias;
import com.fixflow.catalog.domain.DeviceModel;
import com.fixflow.catalog.domain.ProductVariant;
import com.fixflow.catalog.domain.StockStatus;
import com.fixflow.catalog.dto.CompatibilityDtos.CategoryPartsSummary;
import com.fixflow.catalog.dto.CompatibilityDtos.CompatibilityGroupSummary;
import com.fixflow.catalog.dto.CompatibilityDtos.DeviceCompatibilityView;
import com.fixflow.catalog.dto.CompatibilityDtos.DeviceSummary;
import com.fixflow.catalog.dto.CompatibilityDtos.PartOption;
import com.fixflow.catalog.repository.CategoryRepository;
import com.fixflow.catalog.repository.CompatibilityGroupDeviceRepository;
import com.fixflow.catalog.repository.CompatibilityGroupRepository;
import com.fixflow.catalog.repository.DeviceAliasRepository;
import com.fixflow.catalog.repository.DeviceModelRepository;
import com.fixflow.catalog.repository.ProductVariantRepository;
import com.fixflow.common.error.ApiException;
import com.fixflow.config.FixFlowProperties;
import com.fixflow.pricing.domain.PricingFlag;
import com.fixflow.pricing.service.PriceContext;
import com.fixflow.pricing.service.PriceQuote;
import com.fixflow.pricing.service.PricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Answers the question the whole product exists for:
 * <em>"What parts do I have for this phone, how many, and at what price?"</em>
 *
 * <p>One call returns the device, its alias list, every model it shares parts
 * with, and all stock grouped by category — so the Android app can render the
 * entire screen without a second round trip.
 */
@Service
@RequiredArgsConstructor
public class CompatibilityLookupService {

    /** Enough choices to decide, few enough to scan on a phone. */
    private static final int MAX_OPTIONS_PER_CATEGORY = 8;

    private final DeviceModelRepository deviceModelRepository;
    private final DeviceAliasRepository deviceAliasRepository;
    private final CompatibilityGroupRepository compatibilityGroupRepository;
    private final CompatibilityGroupDeviceRepository compatibilityGroupDeviceRepository;
    private final CategoryRepository categoryRepository;
    private final ProductVariantRepository variantRepository;
    private final PricingService pricingService;
    private final FixFlowProperties properties;

    @Transactional
    public DeviceCompatibilityView lookup(UUID shopId, UUID deviceModelId, PricingFlag flag) {
        DeviceModel device = deviceModelRepository.findByIdAndShopId(deviceModelId, shopId)
                .orElseThrow(() -> ApiException.notFound("Device model", deviceModelId));

        // Frequently serviced handsets should float to the top of future searches.
        deviceModelRepository.incrementPopularity(deviceModelId);

        PricingFlag effectiveFlag = flag == null ? PricingFlag.NORMAL : flag;

        DeviceSummary deviceSummary = toSummary(device, aliasesFor(deviceModelId));
        List<DeviceSummary> compatibleModels = loadCompatibleModels(deviceModelId);
        List<CompatibilityGroupSummary> groups = loadGroups(shopId, deviceModelId);
        List<CategoryPartsSummary> categories = loadCategoryBreakdown(shopId, deviceModelId, effectiveFlag);

        int totalAvailable = categories.stream().mapToInt(CategoryPartsSummary::totalAvailable).sum();
        int inStock = (int) categories.stream().filter(c -> c.totalAvailable() > 0).count();
        int outOfStock = categories.size() - inStock;

        return new DeviceCompatibilityView(deviceSummary, compatibleModels, groups, categories,
                totalAvailable, inStock, outOfStock, effectiveFlag);
    }

    // -----------------------------------------------------------------

    private List<DeviceSummary> loadCompatibleModels(UUID deviceModelId) {
        List<DeviceModelRepository.DeviceSearchRow> rows =
                deviceModelRepository.findCompatibleModels(deviceModelId);
        if (rows.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = rows.stream().map(DeviceModelRepository.DeviceSearchRow::getId).toList();
        Map<UUID, List<String>> aliasesByDevice = deviceAliasRepository.findByDeviceModelIdInOrderByAliasAsc(ids)
                .stream()
                .collect(Collectors.groupingBy(DeviceAlias::getDeviceModelId,
                        Collectors.mapping(DeviceAlias::getAlias, Collectors.toList())));

        return rows.stream()
                .map(row -> new DeviceSummary(row.getId(), row.getName(), row.getBrandId(), row.getBrandName(),
                        row.getModelCode(), row.getVariant(), aliasesByDevice.getOrDefault(row.getId(), List.of())))
                .toList();
    }

    private List<CompatibilityGroupSummary> loadGroups(UUID shopId, UUID deviceModelId) {
        List<CompatibilityGroup> groups = compatibilityGroupRepository.findForDevice(shopId, deviceModelId);
        if (groups.isEmpty()) {
            return List.of();
        }

        Map<UUID, String> categoryNames = categoryRepository.findByShopIdOrderBySortOrderAscNameAsc(shopId)
                .stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));

        return groups.stream()
                .map(group -> new CompatibilityGroupSummary(
                        group.getId(),
                        group.getCode(),
                        group.getName(),
                        group.getCategoryId(),
                        group.getCategoryId() == null ? null : categoryNames.get(group.getCategoryId()),
                        group.isVerified(),
                        (int) compatibilityGroupDeviceRepository.countDevices(group.getId())))
                .toList();
    }

    /**
     * Groups every compatible variant under its category and rolls up stock and
     * a price range, which is exactly how the device screen is laid out.
     * Categories with no stock are still returned so the shopkeeper can see the
     * gap rather than wonder whether the search missed something.
     */
    private List<CategoryPartsSummary> loadCategoryBreakdown(UUID shopId, UUID deviceModelId,
                                                             PricingFlag flag) {
        List<ProductVariant> variants = variantRepository.findCompatibleWithDevice(shopId, deviceModelId);
        if (variants.isEmpty()) {
            return List.of();
        }

        Map<UUID, Category> categoriesById = categoryRepository
                .findByShopIdOrderBySortOrderAscNameAsc(shopId).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

        Map<UUID, List<ProductVariant>> byCategory = variants.stream()
                .collect(Collectors.groupingBy(v -> v.getProduct().getCategoryId(),
                        LinkedHashMap::new, Collectors.toList()));

        double criticalFactor = properties.getInventory().getCriticalStockFactor();
        List<CategoryPartsSummary> summaries = new ArrayList<>();

        byCategory.forEach((categoryId, categoryVariants) -> {
            Category category = categoriesById.get(categoryId);
            if (category == null || !category.isActive()) {
                return;
            }

            List<PartOption> options = categoryVariants.stream()
                    .map(variant -> toOption(shopId, variant, flag, criticalFactor))
                    // In stock first, then cheapest: what a shopkeeper reaches for.
                    .sorted(Comparator
                            .comparing((PartOption o) -> o.availableQty() <= 0)
                            .thenComparing(PartOption::price, Comparator.nullsLast(BigDecimal::compareTo)))
                    .toList();

            int totalAvailable = options.stream().mapToInt(PartOption::availableQty).sum();
            List<BigDecimal> inStockPrices = options.stream()
                    .filter(o -> o.availableQty() > 0)
                    .map(PartOption::price)
                    .filter(java.util.Objects::nonNull)
                    .sorted()
                    .toList();

            int highestReorderLevel = categoryVariants.stream()
                    .mapToInt(ProductVariant::getReorderLevel)
                    .max()
                    .orElse(0);

            summaries.add(new CategoryPartsSummary(
                    category.getId(),
                    category.getCode(),
                    category.getName(),
                    category.getIcon(),
                    category.getColor(),
                    options.size(),
                    totalAvailable,
                    StockStatus.evaluate(totalAvailable, highestReorderLevel, criticalFactor),
                    inStockPrices.isEmpty() ? null : inStockPrices.get(0),
                    inStockPrices.isEmpty() ? null : inStockPrices.get(inStockPrices.size() - 1),
                    options.size() > MAX_OPTIONS_PER_CATEGORY
                            ? options.subList(0, MAX_OPTIONS_PER_CATEGORY)
                            : options));
        });

        summaries.sort(Comparator.comparingInt(s -> {
            Category category = categoriesById.get(s.categoryId());
            return category == null ? Integer.MAX_VALUE : category.getSortOrder();
        }));
        return summaries;
    }

    private PartOption toOption(UUID shopId, ProductVariant variant, PricingFlag flag, double criticalFactor) {
        PriceQuote quote = pricingService.quote(PriceContext.of(shopId, variant, flag));
        return new PartOption(
                variant.getId(),
                variant.getProduct().getId(),
                variant.getProduct().getName(),
                variant.getVariantName(),
                variant.getSku(),
                variant.getBarcode(),
                variant.getGrade(),
                variant.getQuality(),
                variant.getColor(),
                variant.getOnHandQty(),
                variant.getReservedQty(),
                variant.available(),
                variant.stockStatus(criticalFactor),
                quote.unitPrice(),
                quote.costPrice(),
                quote.marginAmount(),
                quote.sourceLabel(),
                variant.getWarrantyDays(),
                variant.getLocation());
    }

    private List<String> aliasesFor(UUID deviceModelId) {
        return deviceAliasRepository.findByDeviceModelIdOrderByAliasAsc(deviceModelId).stream()
                .map(DeviceAlias::getAlias)
                .toList();
    }

    private DeviceSummary toSummary(DeviceModel device, List<String> aliases) {
        return new DeviceSummary(device.getId(), device.getName(), device.getBrand().getId(),
                device.getBrand().getName(), device.getModelCode(), device.getVariant(), aliases);
    }
}
