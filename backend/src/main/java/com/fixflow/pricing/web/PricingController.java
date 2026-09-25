package com.fixflow.pricing.web;

import com.fixflow.catalog.domain.ProductVariant;
import com.fixflow.catalog.repository.ProductVariantRepository;
import com.fixflow.common.error.ApiException;
import com.fixflow.party.domain.CustomerType;
import com.fixflow.pricing.domain.PriceRule;
import com.fixflow.pricing.domain.PricingFlag;
import com.fixflow.pricing.service.PriceContext;
import com.fixflow.pricing.service.PriceQuote;
import com.fixflow.pricing.service.PricingService;
import com.fixflow.security.Authorize;
import com.fixflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mobistack/pricing")
@RequiredArgsConstructor
@Tag(name = "Pricing")
public class PricingController {

    private final PricingService pricingService;
    private final ProductVariantRepository variantRepository;

    @GetMapping("/quote")
    @PreAuthorize(Authorize.INVENTORY_READ)
    @Operation(summary = "Resolve the selling price for a variant in a given context")
    public PriceQuote quote(@RequestParam UUID variantId,
                            @RequestParam(defaultValue = "NORMAL") PricingFlag flag,
                            @RequestParam(defaultValue = "RETAIL") CustomerType customerType,
                            @RequestParam(defaultValue = "SALE") PriceRule.TransactionType transactionType,
                            @RequestParam(defaultValue = "1") int quantity) {
        ProductVariant variant = variantRepository.findByIdAndShopId(variantId, CurrentUser.shopId())
                .orElseThrow(() -> ApiException.notFound("Product variant", variantId));

        PriceContext context = PriceContext.of(CurrentUser.shopId(), variant, flag)
                .withCustomerType(customerType)
                .withTransactionType(transactionType)
                .withQuantity(quantity);
        return pricingService.quote(context);
    }
}
