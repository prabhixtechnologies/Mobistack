package com.fixflow.inventory.web;

import com.fixflow.common.web.PageResponse;
import com.fixflow.inventory.domain.InventoryReferenceType;
import com.fixflow.inventory.domain.InventoryTransactionType;
import com.fixflow.inventory.dto.InventoryDtos.InventorySnapshot;
import com.fixflow.inventory.dto.InventoryDtos.InventoryTransactionResponse;
import com.fixflow.inventory.dto.InventoryDtos.StockAdjustRequest;
import com.fixflow.inventory.dto.InventoryDtos.StockAlertResponse;
import com.fixflow.inventory.dto.InventoryDtos.StockDamageRequest;
import com.fixflow.inventory.dto.InventoryDtos.StockIssueRequest;
import com.fixflow.inventory.dto.InventoryDtos.StockReceiveRequest;
import com.fixflow.inventory.service.InventoryQueryService;
import com.fixflow.inventory.service.InventoryService;
import com.fixflow.inventory.service.StockMovement;
import com.fixflow.security.Authorize;
import com.fixflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory")
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryQueryService inventoryQueryService;

    @GetMapping("/snapshot")
    @PreAuthorize(Authorize.INVENTORY_READ)
    @Operation(summary = "Headline stock figures for the dashboard")
    public InventorySnapshot snapshot() {
        return inventoryQueryService.snapshot(CurrentUser.shopId());
    }

    @GetMapping("/transactions")
    @PreAuthorize(Authorize.INVENTORY_READ)
    public PageResponse<InventoryTransactionResponse> history(
            @RequestParam(required = false) UUID variantId,
            @PageableDefault(size = 25) Pageable pageable) {
        return PageResponse.of(inventoryQueryService.history(CurrentUser.shopId(), variantId, pageable));
    }

    @GetMapping("/alerts")
    @PreAuthorize(Authorize.INVENTORY_READ)
    public PageResponse<StockAlertResponse> alerts(@PageableDefault(size = 25) Pageable pageable) {
        return PageResponse.of(inventoryQueryService.openAlerts(CurrentUser.shopId(), pageable));
    }

    @PostMapping("/receive")
    @PreAuthorize(Authorize.INVENTORY_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add stock. Posts an IN ledger row.")
    public InventoryTransactionResponse receive(@Valid @RequestBody StockReceiveRequest request) {
        return inventoryQueryService.toResponse(inventoryService.receive(
                CurrentUser.shopId(), request.variantId(), request.quantity(), request.unitCost(),
                request.reason(), request.batchNo()));
    }

    @PostMapping("/issue")
    @PreAuthorize(Authorize.INVENTORY_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Remove stock without a sale (counter use, sample, etc.)")
    public InventoryTransactionResponse issue(@Valid @RequestBody StockIssueRequest request) {
        return inventoryQueryService.toResponse(inventoryService.issue(
                CurrentUser.shopId(), request.variantId(), request.quantity(),
                InventoryReferenceType.MANUAL, null, request.reason()));
    }

    @PostMapping("/adjust")
    @PreAuthorize(Authorize.INVENTORY_ADJUST)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Stock-take correction. The ledger records the difference, not an overwrite.")
    public InventoryTransactionResponse adjust(@Valid @RequestBody StockAdjustRequest request) {
        return inventoryQueryService.toResponse(inventoryService.adjustTo(
                CurrentUser.shopId(), request.variantId(), request.countedQuantity(), request.reason()));
    }

    @PostMapping("/damage")
    @PreAuthorize(Authorize.INVENTORY_ADJUST)
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryTransactionResponse damage(@Valid @RequestBody StockDamageRequest request) {
        return inventoryQueryService.toResponse(inventoryService.post(CurrentUser.shopId(),
                StockMovement.of(request.variantId(), InventoryTransactionType.DAMAGE, request.quantity())
                        .reason(request.reason())
                        .build()));
    }

    @PostMapping("/variants/{id}/reconcile")
    @PreAuthorize(Authorize.INVENTORY_ADJUST)
    @Operation(summary = "Rebuild cached stock from the ledger and report any drift")
    public InventoryService.StockReconciliation reconcile(@PathVariable UUID id) {
        return inventoryService.reconcile(CurrentUser.shopId(), id);
    }
}
