package com.fixflow.commerce.web;

import com.fixflow.commerce.dto.CommerceDtos.CreateSaleRequest;
import com.fixflow.commerce.dto.CommerceDtos.SaleResponse;
import com.fixflow.commerce.dto.CommerceDtos.VoidSaleRequest;
import com.fixflow.commerce.service.SaleService;
import com.fixflow.common.web.PageResponse;
import com.fixflow.security.Authorize;
import com.fixflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
@RequestMapping("/api/v1/sales")
@RequiredArgsConstructor
@Tag(name = "Sales")
public class SaleController {

    private final SaleService saleService;

    @GetMapping
    @PreAuthorize(Authorize.SALES_READ)
    public PageResponse<SaleResponse> list(@RequestParam(required = false) UUID customerId,
                                           @PageableDefault(size = 25) Pageable pageable) {
        return PageResponse.of(saleService.list(CurrentUser.shopId(), customerId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Authorize.SALES_READ)
    public SaleResponse get(@PathVariable UUID id) {
        return saleService.get(CurrentUser.shopId(), id);
    }

    @GetMapping(value = "/{id}/invoice", produces = MediaType.TEXT_HTML_VALUE)
    @PreAuthorize(Authorize.SALES_READ)
    @Operation(summary = "Printable invoice HTML")
    public String invoice(@PathVariable UUID id) {
        return saleService.invoiceHtml(CurrentUser.shopId(), id);
    }

    @PostMapping
    @PreAuthorize(Authorize.SALES_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public SaleResponse complete(@Valid @RequestBody CreateSaleRequest request) {
        return saleService.complete(CurrentUser.shopId(), request);
    }

    @PostMapping("/{id}/void")
    @PreAuthorize(Authorize.SALES_VOID)
    public SaleResponse voidSale(@PathVariable UUID id, @RequestBody(required = false) VoidSaleRequest request) {
        return saleService.voidSale(CurrentUser.shopId(), id, request);
    }
}
