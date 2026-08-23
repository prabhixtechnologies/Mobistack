package com.fixflow.pricing.service;

import com.fixflow.catalog.domain.Product;
import com.fixflow.catalog.domain.ProductVariant;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.pricing.domain.PriceList;
import com.fixflow.pricing.domain.PriceListItem;
import com.fixflow.pricing.domain.PriceRule;
import com.fixflow.pricing.domain.PricingFlag;
import com.fixflow.pricing.repository.PriceListItemRepository;
import com.fixflow.pricing.repository.PriceListRepository;
import com.fixflow.pricing.repository.PriceRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Decides which price applies in a given selling context.
 *
 * <p>Precedence, highest first:
 * <ol>
 *   <li>an explicit {@link PriceList} entry for the variant;</li>
 *   <li>the best matching {@link PriceRule};</li>
 *   <li>the variant's own field for that pricing flag.</li>
 * </ol>
 *
 * <p>Whatever wins is clamped to the variant's minimum price, so no rule and no
 * cashier can sell below the floor. Nothing here branches on a hard-coded shop
 * policy: the rules live in the database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingService {

    /** Where each flag looks first when no list or rule matches. */
    private static final Map<PricingFlag, List<PriceRule.BaseField>> FLAG_FALLBACKS = Map.of(
            PricingFlag.NORMAL, List.of(PriceRule.BaseField.RETAIL_PRICE),
            PricingFlag.WHOLESALE, List.of(PriceRule.BaseField.WHOLESALE_PRICE, PriceRule.BaseField.RETAIL_PRICE),
            PricingFlag.REPAIR, List.of(PriceRule.BaseField.REPAIR_PRICE, PriceRule.BaseField.RETAIL_PRICE),
            PricingFlag.VIP, List.of(PriceRule.BaseField.WHOLESALE_PRICE, PriceRule.BaseField.RETAIL_PRICE),
            PricingFlag.CLEARANCE, List.of(PriceRule.BaseField.CLEARANCE_PRICE, PriceRule.BaseField.RETAIL_PRICE),
            PricingFlag.OLD_STOCK, List.of(PriceRule.BaseField.CLEARANCE_PRICE, PriceRule.BaseField.RETAIL_PRICE),
            PricingFlag.CUSTOM, List.of(PriceRule.BaseField.RETAIL_PRICE)
    );

    private final PriceRuleRepository priceRuleRepository;
    private final PriceListRepository priceListRepository;
    private final PriceListItemRepository priceListItemRepository;

    @Transactional(readOnly = true)
    public PriceQuote quote(PriceContext context) {
        ProductVariant variant = context.variant();
        BigDecimal minPrice = variant.getMinPrice();

        Optional<PriceQuote> fromList = quoteFromPriceList(context);
        if (fromList.isPresent()) {
            return clamp(fromList.get(), minPrice, true);
        }

        Optional<PriceQuote> fromRule = quoteFromRules(context);
        if (fromRule.isPresent()) {
            return clamp(fromRule.get(), minPrice, true);
        }

        return clamp(quoteFromVariantFields(context), minPrice, true);
    }

    /** Convenience for callers that only have a variant and a flag. */
    @Transactional(readOnly = true)
    public PriceQuote quote(UUID shopId, ProductVariant variant, PricingFlag flag) {
        return quote(PriceContext.of(shopId, variant, flag));
    }

    // -----------------------------------------------------------------
    // Resolution steps
    // -----------------------------------------------------------------

    private Optional<PriceQuote> quoteFromPriceList(PriceContext context) {
        List<PriceList> lists = priceListRepository.findActive(context.shopId(), context.at());
        if (lists.isEmpty()) {
            return Optional.empty();
        }

        List<PriceList> applicable = lists.stream()
                .filter(list -> list.getPricingFlag() == null || list.getPricingFlag() == context.flag())
                .filter(list -> list.getCustomerType() == null || list.getCustomerType() == context.customerType())
                .sorted(Comparator.comparingInt(PriceList::getPriority))
                .toList();
        if (applicable.isEmpty()) {
            return Optional.empty();
        }

        List<UUID> listIds = applicable.stream().map(PriceList::getId).toList();
        List<PriceListItem> items = priceListItemRepository.findApplicable(
                context.variant().getId(), listIds, context.quantity());
        if (items.isEmpty()) {
            return Optional.empty();
        }

        // findApplicable orders by quantity tier; among equal tiers prefer the
        // highest-priority list.
        Map<UUID, Integer> priorityByList = applicable.stream()
                .collect(java.util.stream.Collectors.toMap(PriceList::getId, PriceList::getPriority));
        PriceListItem best = items.stream()
                .min(Comparator
                        .comparingInt((PriceListItem i) -> -i.getMinQuantity())
                        .thenComparingInt(i -> priorityByList.getOrDefault(i.getPriceListId(), Integer.MAX_VALUE)))
                .orElseThrow();

        PriceList owningList = applicable.stream()
                .filter(l -> l.getId().equals(best.getPriceListId()))
                .findFirst()
                .orElseThrow();

        return Optional.of(PriceQuote.build(best.getPrice(), context.variant().getCostPrice(), context.flag(),
                "PRICE_LIST", owningList.getName(), owningList.getId(), false, context.variant().getMinPrice()));
    }

    private Optional<PriceQuote> quoteFromRules(PriceContext context) {
        List<PriceRule> active = priceRuleRepository.findActiveRules(context.shopId(), context.at());
        if (active.isEmpty()) {
            return Optional.empty();
        }

        return active.stream()
                .filter(rule -> matches(rule, context))
                .min(Comparator
                        .comparingInt(PriceRule::getPriority)
                        .thenComparing(Comparator.comparingInt(PriceRule::specificity).reversed()))
                .map(rule -> applyRule(rule, context));
    }

    private boolean matches(PriceRule rule, PriceContext context) {
        ProductVariant variant = context.variant();
        Product product = variant.getProduct();

        boolean scopeMatches = switch (rule.getScopeType()) {
            case ALL -> true;
            case VARIANT -> variant.getId().equals(rule.getScopeId());
            case PRODUCT -> product != null && product.getId().equals(rule.getScopeId());
            case CATEGORY -> product != null && product.getCategoryId().equals(rule.getScopeId());
            case BRAND -> product != null && rule.getScopeId() != null
                    && rule.getScopeId().equals(product.getBrandId());
        };
        if (!scopeMatches) {
            return false;
        }

        if (rule.getPricingFlag() != null && rule.getPricingFlag() != context.flag()) {
            return false;
        }
        if (rule.getCustomerType() != null && rule.getCustomerType() != context.customerType()) {
            return false;
        }
        if (rule.getTransactionType() != null && rule.getTransactionType() != context.transactionType()) {
            return false;
        }
        if (rule.getMinQuantity() != null && context.quantity() < rule.getMinQuantity()) {
            return false;
        }
        if (rule.getSupplierId() != null && !rule.getSupplierId().equals(variant.getSupplierId())) {
            return false;
        }
        if (rule.getMinStockAgeDays() != null && stockAgeDays(variant, context.at()) < rule.getMinStockAgeDays()) {
            return false;
        }
        return true;
    }

    private PriceQuote applyRule(PriceRule rule, PriceContext context) {
        ProductVariant variant = context.variant();
        BigDecimal base = fieldValue(variant, rule.getBaseField());
        if (base == null) {
            base = variant.getRetailPrice();
        }

        BigDecimal price = switch (rule.getStrategy()) {
            case USE_FIELD -> base;
            case PERCENT_OFF -> base.subtract(percentOf(base, rule.getPercentage()));
            case AMOUNT_OFF -> base.subtract(nullToZero(rule.getAmount()));
            case MARKUP_ON_COST -> variant.getCostPrice().add(percentOf(variant.getCostPrice(),
                    rule.getPercentage()));
            case FIXED_PRICE -> nullToZero(rule.getAmount());
        };

        if (price.signum() < 0) {
            price = BigDecimal.ZERO;
        }

        PriceQuote quote = PriceQuote.build(price, variant.getCostPrice(), context.flag(),
                "RULE", rule.getName(), rule.getId(), false, variant.getMinPrice());
        return rule.isRespectMinPrice() ? quote : new PriceQuote(quote.unitPrice(), quote.costPrice(),
                quote.marginAmount(), quote.marginPercent(), quote.appliedFlag(), quote.source(),
                quote.sourceLabel(), quote.sourceId(), false, null);
    }

    private PriceQuote quoteFromVariantFields(PriceContext context) {
        ProductVariant variant = context.variant();
        List<PriceRule.BaseField> fallbacks = FLAG_FALLBACKS.getOrDefault(context.flag(),
                List.of(PriceRule.BaseField.RETAIL_PRICE));

        for (PriceRule.BaseField field : fallbacks) {
            BigDecimal value = fieldValue(variant, field);
            if (value != null && value.signum() > 0) {
                return PriceQuote.build(value, variant.getCostPrice(), context.flag(),
                        "VARIANT_FIELD", field.name(), variant.getId(), false, variant.getMinPrice());
            }
        }

        return PriceQuote.build(variant.getRetailPrice(), variant.getCostPrice(), context.flag(),
                "VARIANT_FIELD", PriceRule.BaseField.RETAIL_PRICE.name(), variant.getId(), false,
                variant.getMinPrice());
    }

    // -----------------------------------------------------------------
    // Guards and helpers
    // -----------------------------------------------------------------

    /**
     * Rejects a manually typed price that undercuts the floor. Called by the POS
     * before a discount is accepted; the client-side check is only a courtesy.
     */
    public void assertAboveMinimum(ProductVariant variant, BigDecimal proposedPrice) {
        BigDecimal minPrice = variant.getMinPrice();
        if (minPrice == null || minPrice.signum() <= 0) {
            return;
        }
        if (proposedPrice.compareTo(minPrice) < 0) {
            throw new ApiException(ErrorCode.PRICE_BELOW_MINIMUM,
                    "%s cannot be sold below its minimum price of %s".formatted(
                            variant.getVariantName(), minPrice.toPlainString()),
                    Map.of("minPrice", minPrice, "proposedPrice", proposedPrice));
        }
    }

    private PriceQuote clamp(PriceQuote quote, BigDecimal minPrice, boolean enforce) {
        if (!enforce || minPrice == null || minPrice.signum() <= 0
                || quote.unitPrice().compareTo(minPrice) >= 0) {
            return quote;
        }
        return PriceQuote.build(minPrice, quote.costPrice(), quote.appliedFlag(), quote.source(),
                quote.sourceLabel(), quote.sourceId(), true, minPrice);
    }

    private static long stockAgeDays(ProductVariant variant, Instant at) {
        Instant since = variant.getFirstStockedAt() != null ? variant.getFirstStockedAt()
                : variant.getLastPurchasedAt();
        if (since == null) {
            return 0;
        }
        return Duration.between(since, at).toDays();
    }

    private static BigDecimal fieldValue(ProductVariant variant, PriceRule.BaseField field) {
        return switch (field) {
            case COST_PRICE -> variant.getCostPrice();
            case RETAIL_PRICE -> variant.getRetailPrice();
            case WHOLESALE_PRICE -> variant.getWholesalePrice();
            case REPAIR_PRICE -> variant.getRepairPrice();
            case MIN_PRICE -> variant.getMinPrice();
            case CLEARANCE_PRICE -> variant.getClearancePrice();
        };
    }

    private static BigDecimal percentOf(BigDecimal base, BigDecimal percentage) {
        if (percentage == null) {
            return BigDecimal.ZERO;
        }
        return base.multiply(percentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
