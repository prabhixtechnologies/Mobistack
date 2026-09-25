package com.fixflow.sync.web;

import com.fixflow.catalog.service.DeviceService;
import com.fixflow.catalog.service.ProductService;
import com.fixflow.commerce.dto.CommerceDtos.CreateSaleRequest;
import com.fixflow.commerce.service.SaleService;
import com.fixflow.commons.domain.CatalogEntities.CatalogDevice;
import com.fixflow.commons.service.CommonsCatalogService;
import com.fixflow.inventory.dto.InventoryDtos.StockReceiveRequest;
import com.fixflow.inventory.service.InventoryQueryService;
import com.fixflow.inventory.service.InventoryService;
import com.fixflow.party.dto.PartyDtos.CustomerRequest;
import com.fixflow.party.service.PartyService;
import com.fixflow.repair.domain.RepairStatus;
import com.fixflow.repair.dto.RepairDtos.CreateRepairRequest;
import com.fixflow.repair.dto.RepairDtos.UpdateRepairRequest;
import com.fixflow.repair.service.RepairService;
import com.fixflow.report.service.ReportService;
import com.fixflow.security.Authorize;
import com.fixflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/mobistack/sync")
@RequiredArgsConstructor
@Tag(name = "Sync")
public class SyncController {

    private final SaleService saleService;
    private final InventoryService inventoryService;
    private final InventoryQueryService inventoryQueryService;
    private final ProductService productService;
    private final RepairService repairService;
    private final ReportService reportService;
    private final DeviceService deviceService;
    private final PartyService partyService;
    private final CommonsCatalogService commonsCatalog;

    public record RepairStatusOp(java.util.UUID repairId, String status) {
    }

    public record SyncOperation(
            @NotBlank String type,
            @NotBlank String idempotencyKey,
            CreateSaleRequest sale,
            StockReceiveRequest receive,
            CreateRepairRequest repair,
            CustomerRequest customer,
            RepairStatusOp repairStatus
    ) {
    }

    public record SyncRequest(List<SyncOperation> operations) {
    }

    public record SyncResult(String idempotencyKey, String status, String message) {
    }

    @GetMapping("/snapshot")
    @PreAuthorize(Authorize.INVENTORY_READ)
    public Map<String, Object> snapshot() {
        var shopId = CurrentUser.shopId();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pulledAt", java.time.Instant.now().toString());
        body.put("variants", productService.searchVariants(shopId, null, null, null, null, false, false,
                PageRequest.of(0, 200)).getContent());
        body.put("sales", saleService.list(shopId, null, PageRequest.of(0, 40)).getContent());
        body.put("repairs", repairService.list(shopId, null, PageRequest.of(0, 40)).getContent());
        body.put("customers", partyService.searchCustomers(shopId, "", PageRequest.of(0, 80)).getContent());
        body.put("devices", deviceService.list(shopId, null, PageRequest.of(0, 200)).getContent());
        var today = ReportService.resolve("today", null, null, java.time.ZoneId.of("Asia/Kolkata"));
        var sales = reportService.sales(shopId, today);
        var repairs = reportService.repairs(shopId);
        Map<String, Object> dashboard = new LinkedHashMap<>();
        Map<String, Object> salesCard = new LinkedHashMap<>();
        salesCard.put("todaySales", sales.sales());
        salesCard.put("todayProfit", sales.profit());
        salesCard.put("todayTransactions", sales.transactions());
        dashboard.put("sales", salesCard);
        dashboard.put("repairs", java.util.Collections.singletonMap("pending", repairs.pending()));
        dashboard.put("inventory", inventoryQueryService.snapshot(shopId));
        dashboard.put("alerts", inventoryQueryService.openAlerts(shopId, PageRequest.of(0, 8)).getContent());
        body.put("dashboard", dashboard);
        body.put("commons", commonsSnapshot());
        return body;
    }

    /**
     * A slice of the shared catalog so the counter app can search phones offline.
     *
     * <p>Not the whole graph: brands plus the most-looked-up models. Fitments are fetched live
     * when the shop is online; caching every edge would dwarf the rest of the snapshot.
     */
    private Map<String, Object> commonsSnapshot() {
        Map<String, Object> commons = new LinkedHashMap<>();
        commons.put("brands", commonsCatalog.listBrands().stream()
                .map(brand -> Map.<String, Object>of(
                        "id", brand.getId(),
                        "name", brand.getName()))
                .toList());
        List<CatalogDevice> popular = commonsCatalog.popularDevices(200);
        var names = commonsCatalog.brandNames(popular.stream()
                .map(CatalogDevice::getBrandId)
                .distinct()
                .toList());
        commons.put("devices", popular.stream()
                .map(device -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", device.getId());
                    row.put("name", device.getName());
                    row.put("brandId", device.getBrandId());
                    row.put("brandName", names.get(device.getBrandId()));
                    row.put("modelCode", device.getModelCode());
                    row.put("variant", device.getVariant());
                    return row;
                })
                .toList());
        return commons;
    }

    @PostMapping
    @PreAuthorize(Authorize.INVENTORY_WRITE + " or hasAuthority('SALES_WRITE') or hasAuthority('REPAIR_WRITE')")
    public List<SyncResult> push(@Valid @RequestBody SyncRequest request,
                                 jakarta.servlet.http.HttpServletRequest http) {
        List<SyncResult> results = new ArrayList<>();
        if (request.operations() == null) {
            return results;
        }
        String deviceId = com.fixflow.common.web.ClientRequests.deviceId(http);
        for (SyncOperation op : request.operations()) {
            try {
                if ("SALE".equalsIgnoreCase(op.type()) && op.sale() != null) {
                    CreateSaleRequest keyed = new CreateSaleRequest(op.sale().customerId(), op.sale().pricingFlag(),
                            op.sale().discount(), op.sale().notes(), op.idempotencyKey(), op.sale().deviceId(),
                            op.sale().items(), op.sale().payments());
                    saleService.complete(CurrentUser.shopId(), keyed);
                    results.add(new SyncResult(op.idempotencyKey(), "SYNCED", "Sale accepted"));
                } else if ("RECEIVE".equalsIgnoreCase(op.type()) && op.receive() != null) {
                    inventoryQueryService.toResponse(inventoryService.receive(
                            CurrentUser.shopId(), op.receive().variantId(), op.receive().quantity(),
                            op.receive().unitCost(), op.receive().reason(), op.receive().batchNo(),
                            op.idempotencyKey(), deviceId));
                    results.add(new SyncResult(op.idempotencyKey(), "SYNCED", "Stock received"));
                } else if ("REPAIR".equalsIgnoreCase(op.type()) && op.repair() != null) {
                    CreateRepairRequest repair = op.repair();
                    CreateRepairRequest keyed = new CreateRepairRequest(
                            repair.customerId(), repair.deviceModelId(), repair.technicianUserId(),
                            repair.problem(), repair.imei(), repair.estimatedCost(), repair.laborCharge(),
                            repair.laborCost(), repair.customerNotes(), repair.internalNotes(),
                            repair.expectedAt(), op.idempotencyKey());
                    repairService.create(CurrentUser.shopId(), keyed);
                    results.add(new SyncResult(op.idempotencyKey(), "SYNCED", "Repair accepted"));
                } else if ("CUSTOMER".equalsIgnoreCase(op.type()) && op.customer() != null) {
                    partyService.createCustomerForSync(CurrentUser.shopId(), op.customer());
                    results.add(new SyncResult(op.idempotencyKey(), "SYNCED", "Customer accepted"));
                } else if ("REPAIR_STATUS".equalsIgnoreCase(op.type()) && op.repairStatus() != null
                        && op.repairStatus().repairId() != null && op.repairStatus().status() != null) {
                    RepairStatus status = RepairStatus.valueOf(op.repairStatus().status().trim().toUpperCase());
                    repairService.update(CurrentUser.shopId(), op.repairStatus().repairId(),
                            new UpdateRepairRequest(status, null, null, null, null, null, null));
                    results.add(new SyncResult(op.idempotencyKey(), "SYNCED", "Repair status accepted"));
                } else {
                    results.add(new SyncResult(op.idempotencyKey(), "FAILED", "Unknown operation"));
                }
            } catch (Exception ex) {
                results.add(new SyncResult(op.idempotencyKey(), "FAILED", ex.getMessage()));
            }
        }
        return results;
    }
}
