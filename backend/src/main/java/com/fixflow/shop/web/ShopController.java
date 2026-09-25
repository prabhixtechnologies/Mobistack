package com.fixflow.shop.web;

import com.fixflow.security.Authorize;
import com.fixflow.security.CurrentUser;
import com.fixflow.shop.dto.ShopDtos.ShopResponse;
import com.fixflow.shop.dto.ShopDtos.UpdateShopRequest;
import com.fixflow.shop.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mobistack/shop")
@RequiredArgsConstructor
@Tag(name = "Shop")
public class ShopController {

    private final ShopService shopService;

    @GetMapping
    @PreAuthorize(Authorize.SETTINGS_READ)
    @Operation(summary = "The caller's shop profile")
    public ShopResponse get() {
        return shopService.get(CurrentUser.shopId());
    }

    @PutMapping
    @PreAuthorize(Authorize.SETTINGS_WRITE)
    @Operation(summary = "Update shop profile and settings")
    public ShopResponse update(@Valid @RequestBody UpdateShopRequest request) {
        return shopService.update(CurrentUser.shopId(), request);
    }
}
