package com.fixflow.commerce.web;

import com.fixflow.commerce.dto.CommerceDtos.CreatePurchaseRequest;
import com.fixflow.commerce.dto.CommerceDtos.PurchaseResponse;
import com.fixflow.commerce.service.PurchaseService;
import com.fixflow.common.web.PageResponse;
import com.fixflow.security.Authorize;
import com.fixflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mobistack/purchases")
@RequiredArgsConstructor
@Tag(name = "Purchases")
public class PurchaseController {

    private final PurchaseService purchaseService;

    @GetMapping
    @PreAuthorize(Authorize.PURCHASE_READ)
    public PageResponse<PurchaseResponse> list(@PageableDefault(size = 25) Pageable pageable) {
        return PageResponse.of(purchaseService.list(CurrentUser.shopId(), pageable));
    }

    @GetMapping(params = "id")
    @PreAuthorize(Authorize.PURCHASE_READ)
    public PurchaseResponse get(@RequestParam UUID id) {
        return purchaseService.get(CurrentUser.shopId(), id);
    }

    @PostMapping
    @PreAuthorize(Authorize.PURCHASE_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseResponse receive(@Valid @RequestBody CreatePurchaseRequest request) {
        return purchaseService.receive(CurrentUser.shopId(), request);
    }

    @PostMapping("/cancel")
    @PreAuthorize(Authorize.PURCHASE_WRITE)
    public PurchaseResponse cancel(@RequestParam UUID id, @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return purchaseService.cancel(CurrentUser.shopId(), id, reason);
    }
}
