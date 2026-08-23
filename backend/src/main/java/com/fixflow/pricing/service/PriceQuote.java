package com.fixflow.pricing.service;

import com.fixflow.pricing.domain.PricingFlag;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * The answer to "what does this part cost this customer right now", plus the
 * reasoning. {@code source} is surfaced in the UI so a shopkeeper can see
 * <em>why</em> a price differs from the sticker.
 */
@Schema(name = "PriceQuote")
public record PriceQuote(
        BigDecimal unitPrice,
        BigDecimal costPrice,
        BigDecimal marginAmount,
        BigDecimal marginPercent,
        PricingFlag appliedFlag,
        String source,
        String sourceLabel,
        UUID sourceId,
        boolean clampedToMinimum,
        BigDecimal minPrice
) {

    public static PriceQuote build(BigDecimal unitPrice, BigDecimal costPrice, PricingFlag flag,
                                   String source, String sourceLabel, UUID sourceId,
                                   boolean clamped, BigDecimal minPrice) {
        BigDecimal price = unitPrice.setScale(2, RoundingMode.HALF_UP);
        BigDecimal cost = (costPrice == null ? BigDecimal.ZERO : costPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal margin = price.subtract(cost);
        BigDecimal marginPercent = price.signum() == 0
                ? BigDecimal.ZERO
                : margin.multiply(BigDecimal.valueOf(100)).divide(price, 2, RoundingMode.HALF_UP);
        return new PriceQuote(price, cost, margin, marginPercent, flag, source, sourceLabel, sourceId,
                clamped, minPrice);
    }

    public BigDecimal lineTotal(int quantity) {
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }
}
