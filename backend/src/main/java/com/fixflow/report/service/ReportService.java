package com.fixflow.report.service;

import com.fixflow.catalog.domain.ProductVariant;
import com.fixflow.catalog.repository.ProductVariantRepository;
import com.fixflow.commerce.domain.PurchaseStatus;
import com.fixflow.commerce.domain.SaleStatus;
import com.fixflow.commerce.repository.PurchaseRepository;
import com.fixflow.commerce.repository.SaleRepository;
import com.fixflow.config.FixFlowProperties;
import com.fixflow.inventory.service.InventoryQueryService;
import com.fixflow.party.domain.Customer;
import com.fixflow.party.domain.Supplier;
import com.fixflow.party.repository.CustomerRepository;
import com.fixflow.party.repository.SupplierRepository;
import com.fixflow.repair.domain.RepairStatus;
import com.fixflow.repair.repository.RepairJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final RepairJobRepository repairRepository;
    private final ProductVariantRepository variantRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryQueryService inventoryQueryService;
    private final FixFlowProperties properties;

    public record Range(Instant from, Instant to) {
    }

    public record SalesReport(BigDecimal sales, BigDecimal profit, long transactions) {
    }

    public record RepairPipeline(long received, long diagnosing, long inRepair, long ready, long delivered,
                                 long pending) {
    }

    public record DeadStockRow(UUID variantId, String name, int stock, BigDecimal stockValue, Instant lastSoldAt,
                               long daysInactive, String suggestion) {
    }

    public record ReportBundle(
            SalesReport sales,
            SalesReport purchases,
            BigDecimal repairRevenue,
            RepairPipeline repairs,
            List<DeadStockRow> deadStock,
            List<Customer> customerOutstanding,
            List<Supplier> supplierOutstanding
    ) {
    }

    @Transactional(readOnly = true)
    public SalesReport sales(UUID shopId, Range range) {
        return new SalesReport(
                saleRepository.sumTotal(shopId, SaleStatus.COMPLETED, range.from(), range.to()),
                saleRepository.sumProfit(shopId, SaleStatus.COMPLETED, range.from(), range.to()),
                saleRepository.countByShopIdAndStatusAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                        shopId, SaleStatus.COMPLETED, range.from(), range.to()));
    }

    @Transactional(readOnly = true)
    public SalesReport purchases(UUID shopId, Range range) {
        return new SalesReport(
                purchaseRepository.sumTotal(shopId, PurchaseStatus.RECEIVED, range.from(), range.to()),
                BigDecimal.ZERO,
                0);
    }

    @Transactional(readOnly = true)
    public RepairPipeline repairs(UUID shopId) {
        long received = repairRepository.countByShopIdAndStatus(shopId, RepairStatus.RECEIVED);
        long diagnosing = repairRepository.countByShopIdAndStatus(shopId, RepairStatus.DIAGNOSING);
        long inRepair = repairRepository.countByShopIdAndStatus(shopId, RepairStatus.IN_REPAIR)
                + repairRepository.countByShopIdAndStatus(shopId, RepairStatus.WAITING_FOR_PART);
        long ready = repairRepository.countByShopIdAndStatus(shopId, RepairStatus.READY);
        long delivered = repairRepository.countByShopIdAndStatus(shopId, RepairStatus.DELIVERED);
        return new RepairPipeline(received, diagnosing, inRepair, ready, delivered,
                received + diagnosing + inRepair + ready);
    }

    @Transactional(readOnly = true)
    public List<DeadStockRow> deadStock(UUID shopId) {
        Instant cutoff = Instant.now().minus(properties.getInventory().getDeadStockDays(), ChronoUnit.DAYS);
        return variantRepository.findDeadStock(shopId, cutoff, PageRequest.of(0, 50)).stream()
                .map(this::toDead)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReportBundle bundle(UUID shopId, Range range) {
        return new ReportBundle(
                sales(shopId, range),
                purchases(shopId, range),
                repairRepository.sumDeliveredTotal(shopId, RepairStatus.DELIVERED, range.from(), range.to()),
                repairs(shopId),
                deadStock(shopId),
                customerRepository.search(shopId, null, "", PageRequest.of(0, 25)).getContent().stream()
                        .filter(c -> c.getOutstandingAmount().signum() > 0)
                        .toList(),
                supplierRepository.search(shopId, null, "", PageRequest.of(0, 25)).getContent().stream()
                        .filter(s -> s.getOutstandingAmount().signum() > 0)
                        .toList());
    }

    public static Range resolve(String preset, Instant from, Instant to, ZoneId zone) {
        if (from != null && to != null) {
            return new Range(from, to);
        }
        LocalDate today = LocalDate.now(zone);
        return switch (preset == null ? "this_month" : preset.toLowerCase()) {
            case "today" -> new Range(today.atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant());
            case "yesterday" -> new Range(today.minusDays(1).atStartOfDay(zone).toInstant(),
                    today.atStartOfDay(zone).toInstant());
            case "7d", "7days" -> new Range(today.minusDays(6).atStartOfDay(zone).toInstant(),
                    today.plusDays(1).atStartOfDay(zone).toInstant());
            case "last_month" -> new Range(today.minusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant(),
                    today.withDayOfMonth(1).atStartOfDay(zone).toInstant());
            default -> new Range(today.withDayOfMonth(1).atStartOfDay(zone).toInstant(),
                    today.plusDays(1).atStartOfDay(zone).toInstant());
        };
    }

    private DeadStockRow toDead(ProductVariant variant) {
        Instant last = variant.getLastSoldAt() != null ? variant.getLastSoldAt() : variant.getFirstStockedAt();
        long days = last == null ? properties.getInventory().getDeadStockDays()
                : ChronoUnit.DAYS.between(last, Instant.now());
        String suggestion = days > 180 ? "Clearance" : days > 120 ? "Discount" : "Bundle";
        return new DeadStockRow(variant.getId(), variant.getVariantName(), variant.getOnHandQty(),
                variant.getCostPrice().multiply(BigDecimal.valueOf(variant.getOnHandQty())),
                variant.getLastSoldAt(), days, suggestion);
    }
}
