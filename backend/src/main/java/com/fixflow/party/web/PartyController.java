package com.fixflow.party.web;

import com.fixflow.common.web.PageResponse;
import com.fixflow.party.dto.PartyDtos.CustomerRequest;
import com.fixflow.party.dto.PartyDtos.CustomerResponse;
import com.fixflow.party.dto.PartyDtos.SupplierRequest;
import com.fixflow.party.dto.PartyDtos.SupplierResponse;
import com.fixflow.party.service.PartyService;
import com.fixflow.security.Authorize;
import com.fixflow.security.CurrentUser;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Parties")
public class PartyController {

    private final PartyService partyService;

    @GetMapping("/customers")
    @PreAuthorize(Authorize.CUSTOMER_READ)
    public PageResponse<CustomerResponse> customers(@RequestParam(required = false) String q,
                                                    @PageableDefault(size = 25) Pageable pageable) {
        return PageResponse.of(partyService.searchCustomers(CurrentUser.shopId(), q, pageable));
    }

    @GetMapping("/customers/{id}")
    @PreAuthorize(Authorize.CUSTOMER_READ)
    public CustomerResponse customer(@PathVariable UUID id) {
        return partyService.getCustomer(CurrentUser.shopId(), id);
    }

    @PostMapping("/customers")
    @PreAuthorize(Authorize.CUSTOMER_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse createCustomer(@Valid @RequestBody CustomerRequest request) {
        return partyService.createCustomer(CurrentUser.shopId(), request);
    }

    @PutMapping("/customers/{id}")
    @PreAuthorize(Authorize.CUSTOMER_WRITE)
    public CustomerResponse updateCustomer(@PathVariable UUID id, @Valid @RequestBody CustomerRequest request) {
        return partyService.updateCustomer(CurrentUser.shopId(), id, request);
    }

    @GetMapping("/suppliers")
    @PreAuthorize(Authorize.SUPPLIER_READ)
    public PageResponse<SupplierResponse> suppliers(@RequestParam(required = false) String q,
                                                    @PageableDefault(size = 25) Pageable pageable) {
        return PageResponse.of(partyService.searchSuppliers(CurrentUser.shopId(), q, pageable));
    }

    @GetMapping("/suppliers/{id}")
    @PreAuthorize(Authorize.SUPPLIER_READ)
    public SupplierResponse supplier(@PathVariable UUID id) {
        return partyService.getSupplier(CurrentUser.shopId(), id);
    }

    @PostMapping("/suppliers")
    @PreAuthorize(Authorize.SUPPLIER_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierResponse createSupplier(@Valid @RequestBody SupplierRequest request) {
        return partyService.createSupplier(CurrentUser.shopId(), request);
    }

    @PutMapping("/suppliers/{id}")
    @PreAuthorize(Authorize.SUPPLIER_WRITE)
    public SupplierResponse updateSupplier(@PathVariable UUID id, @Valid @RequestBody SupplierRequest request) {
        return partyService.updateSupplier(CurrentUser.shopId(), id, request);
    }
}
