package com.fixflow.shop.service;

import com.fixflow.audit.service.AuditAction;
import com.fixflow.audit.service.AuditService;
import com.fixflow.common.error.ApiException;
import com.fixflow.shop.domain.Shop;
import com.fixflow.shop.dto.ShopDtos.ShopResponse;
import com.fixflow.shop.dto.ShopDtos.UpdateShopRequest;
import com.fixflow.billing.service.ScreenSeatService;
import com.fixflow.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopRepository shopRepository;
    private final AuditService auditService;
    private final ScreenSeatService screenSeatService;

    @Transactional(readOnly = true)
    public ShopResponse get(UUID shopId) {
        return toResponse(require(shopId));
    }

    @Transactional
    public ShopResponse update(UUID shopId, UpdateShopRequest request) {
        Shop shop = require(shopId);
        shop.setName(request.name().trim());
        shop.setLegalName(request.legalName());
        shop.setPhone(request.phone());
        shop.setEmail(request.email());
        shop.setAddressLine1(request.addressLine1());
        shop.setAddressLine2(request.addressLine2());
        shop.setCity(request.city());
        shop.setState(request.state());
        shop.setPostalCode(request.postalCode());
        if (request.country() != null) {
            shop.setCountry(request.country());
        }
        shop.setGstNumber(request.gstNumber());
        if (request.currencyCode() != null) {
            shop.setCurrencyCode(request.currencyCode().toUpperCase());
        }
        if (request.timezone() != null) {
            shop.setTimezone(request.timezone());
        }
        if (request.invoicePrefix() != null && !request.invoicePrefix().isBlank()) {
            shop.setInvoicePrefix(request.invoicePrefix().trim().toUpperCase());
        }
        if (request.requireCompatibilityApproval() != null) {
            shop.setRequireCompatibilityApproval(request.requireCompatibilityApproval());
        }
        if (request.settings() != null) {
            shop.setSettings(request.settings());
        }
        shopRepository.save(shop);

        auditService.record(AuditAction.SHOP_UPDATED, "Shop", shopId,
                "Updated shop profile \"%s\"".formatted(shop.getName()));
        return toResponse(shop);
    }

    private Shop require(UUID shopId) {
        return shopRepository.findById(shopId)
                .orElseThrow(() -> ApiException.notFound("Shop", shopId));
    }

    private ShopResponse toResponse(Shop shop) {
        var screens = screenSeatService.capacity(shop.getId());
        return new ShopResponse(shop.getId(), shop.getName(), shop.getLegalName(), shop.getPhone(),
                shop.getEmail(), shop.getAddressLine1(), shop.getAddressLine2(), shop.getCity(),
                shop.getState(), shop.getPostalCode(), shop.getCountry(), shop.getGstNumber(),
                shop.getCurrencyCode(), shop.getTimezone(), shop.getInvoicePrefix(), shop.getJoinCode(),
                shop.isRequireCompatibilityApproval(), 1, screens.subscribed(), screens.seats(),
                screens.inUse(), shop.getSettings());
    }
}
