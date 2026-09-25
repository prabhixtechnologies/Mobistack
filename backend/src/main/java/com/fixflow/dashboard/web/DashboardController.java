package com.fixflow.dashboard.web;

import com.fixflow.inventory.dto.InventoryDtos.InventorySnapshot;
import com.fixflow.inventory.dto.InventoryDtos.StockAlertResponse;
import com.fixflow.inventory.service.InventoryQueryService;
import com.fixflow.report.service.ReportService;
import com.fixflow.report.service.ReportService.Range;
import com.fixflow.report.service.ReportService.RepairPipeline;
import com.fixflow.report.service.ReportService.SalesReport;
import com.fixflow.security.Authorize;
import com.fixflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/v1/mobistack/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard")
public class DashboardController {

    private final InventoryQueryService inventoryQueryService;
    private final ReportService reportService;

    @GetMapping
    @PreAuthorize(Authorize.INVENTORY_READ)
    @Operation(summary = "Numbers the Home screen needs in one round trip")
    public DashboardResponse get() {
        var shopId = CurrentUser.shopId();
        Range today = ReportService.resolve("today", null, null, ZoneId.of("Asia/Kolkata"));
        SalesReport sales = reportService.sales(shopId, today);
        RepairPipeline repairs = reportService.repairs(shopId);
        InventorySnapshot inventory = inventoryQueryService.snapshot(shopId);
        List<StockAlertResponse> alerts = inventoryQueryService
                .openAlerts(shopId, PageRequest.of(0, 8))
                .getContent();
        return new DashboardResponse(
                new SalesSummary(sales.sales(), sales.profit(), sales.transactions()),
                new RepairSummary(repairs.received(), repairs.diagnosing(), repairs.inRepair(),
                        repairs.ready(), repairs.delivered(), repairs.pending()),
                inventory,
                alerts);
    }

    public record DashboardResponse(
            SalesSummary sales,
            RepairSummary repairs,
            InventorySnapshot inventory,
            List<StockAlertResponse> alerts
    ) {
    }

    public record SalesSummary(java.math.BigDecimal todaySales, java.math.BigDecimal todayProfit,
                               long todayTransactions) {
    }

    public record RepairSummary(long received, long diagnosing, long inRepair, long ready,
                                long delivered, long pending) {
    }
}
