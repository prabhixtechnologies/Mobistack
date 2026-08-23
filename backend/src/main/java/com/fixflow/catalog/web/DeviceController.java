package com.fixflow.catalog.web;

import com.fixflow.billing.service.BillingService;
import com.fixflow.catalog.dto.CatalogDtos.AddAliasRequest;
import com.fixflow.catalog.dto.CatalogDtos.DeviceModelRequest;
import com.fixflow.catalog.dto.CatalogDtos.DeviceModelResponse;
import com.fixflow.catalog.dto.CompatibilityDtos.DeviceCompatibilityView;
import com.fixflow.catalog.service.CompatibilityLookupService;
import com.fixflow.catalog.service.DeviceService;
import com.fixflow.common.web.PageResponse;
import com.fixflow.pricing.domain.PricingFlag;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Tag(name = "Devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final CompatibilityLookupService compatibilityLookupService;
    private final BillingService billingService;

    @GetMapping
    @PreAuthorize(Authorize.CATALOG_READ)
    public PageResponse<DeviceModelResponse> list(@RequestParam(required = false) UUID brandId,
                                                  @PageableDefault(size = 50) Pageable pageable) {
        return PageResponse.of(deviceService.list(CurrentUser.shopId(), brandId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Authorize.CATALOG_READ)
    public DeviceModelResponse get(@PathVariable UUID id) {
        return deviceService.get(CurrentUser.shopId(), id);
    }

    /**
     * The flagship screen. One call returns the model, its aliases, every
     * compatible phone, and all stock grouped by category with live prices.
     */
    @GetMapping("/{id}/compatibility")
    @PreAuthorize(Authorize.CATALOG_READ)
    @Operation(summary = "What parts fit this phone, how many, and at what price")
    public DeviceCompatibilityView compatibility(@PathVariable UUID id,
                                                 @RequestParam(defaultValue = "NORMAL") PricingFlag flag) {
        billingService.requireCatalog(CurrentUser.shopId());
        return compatibilityLookupService.lookup(CurrentUser.shopId(), id, flag);
    }

    @PostMapping
    @PreAuthorize(Authorize.CATALOG_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceModelResponse create(@Valid @RequestBody DeviceModelRequest request) {
        return deviceService.create(CurrentUser.shopId(), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Authorize.CATALOG_WRITE)
    public DeviceModelResponse update(@PathVariable UUID id, @Valid @RequestBody DeviceModelRequest request) {
        return deviceService.update(CurrentUser.shopId(), id, request);
    }

    @PostMapping("/{id}/aliases")
    @PreAuthorize(Authorize.CATALOG_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceModelResponse addAlias(@PathVariable UUID id, @Valid @RequestBody AddAliasRequest request) {
        return deviceService.addAlias(CurrentUser.shopId(), id, request);
    }

    @DeleteMapping("/aliases/{aliasId}")
    @PreAuthorize(Authorize.CATALOG_WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAlias(@PathVariable UUID aliasId) {
        deviceService.removeAlias(CurrentUser.shopId(), aliasId);
    }
}
