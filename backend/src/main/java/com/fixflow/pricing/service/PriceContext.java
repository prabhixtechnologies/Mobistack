package com.fixflow.pricing.service;

import com.fixflow.catalog.domain.ProductVariant;
import com.fixflow.party.domain.CustomerType;
import com.fixflow.pricing.domain.PriceRule;
import com.fixflow.pricing.domain.PricingFlag;

import java.time.Instant;
import java.util.UUID;

/**
 * Everything the pricing engine is allowed to consider. New inputs
 * (promotions, loyalty tiers) are added here rather than as extra arguments
 * scattered across call sites.
 */
public record PriceContext(
        UUID shopId,
        ProductVariant variant,
        PricingFlag flag,
        CustomerType customerType,
        PriceRule.TransactionType transactionType,
        int quantity,
        Instant at
) {

    public static PriceContext of(UUID shopId, ProductVariant variant, PricingFlag flag) {
        return new PriceContext(shopId, variant, flag == null ? PricingFlag.NORMAL : flag,
                CustomerType.RETAIL, PriceRule.TransactionType.SALE, 1, Instant.now());
    }

    public PriceContext withQuantity(int newQuantity) {
        return new PriceContext(shopId, variant, flag, customerType, transactionType,
                Math.max(1, newQuantity), at);
    }

    public PriceContext withCustomerType(CustomerType type) {
        return new PriceContext(shopId, variant, flag,
                type == null ? CustomerType.RETAIL : type, transactionType, quantity, at);
    }

    public PriceContext withTransactionType(PriceRule.TransactionType type) {
        return new PriceContext(shopId, variant, flag, customerType,
                type == null ? PriceRule.TransactionType.SALE : type, quantity, at);
    }
}
