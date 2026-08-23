package com.fixflow.pricing.service;

import com.fixflow.catalog.domain.Product;
import com.fixflow.catalog.domain.ProductVariant;
import com.fixflow.party.domain.CustomerType;
import com.fixflow.pricing.domain.PriceRule;
import com.fixflow.pricing.domain.PricingFlag;
import com.fixflow.pricing.repository.PriceListItemRepository;
import com.fixflow.pricing.repository.PriceListRepository;
import com.fixflow.pricing.repository.PriceRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private PriceRuleRepository priceRuleRepository;
    @Mock
    private PriceListRepository priceListRepository;
    @Mock
    private PriceListItemRepository priceListItemRepository;

    private PricingService pricingService;
    private ProductVariant variant;
    private UUID shopId;

    @BeforeEach
    void setUp() {
        pricingService = new PricingService(priceRuleRepository, priceListRepository, priceListItemRepository);
        shopId = UUID.randomUUID();

        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setCategoryId(UUID.randomUUID());
        product.setName("iPhone 11 Display");

        variant = new ProductVariant();
        variant.setId(UUID.randomUUID());
        variant.setProduct(product);
        variant.setVariantName("iPhone 11 Display / GX / A+");
        variant.setCostPrice(new BigDecimal("2800.00"));
        variant.setRetailPrice(new BigDecimal("4500.00"));
        variant.setWholesalePrice(new BigDecimal("4000.00"));
        variant.setRepairPrice(new BigDecimal("4800.00"));
        variant.setMinPrice(new BigDecimal("3500.00"));
        variant.setClearancePrice(new BigDecimal("3200.00"));

        lenient().when(priceListRepository.findActive(eq(shopId), any(Instant.class))).thenReturn(List.of());
        lenient().when(priceRuleRepository.findActiveRules(eq(shopId), any(Instant.class))).thenReturn(List.of());
    }

    @Test
    void normalFlagUsesRetail() {
        PriceQuote quote = pricingService.quote(shopId, variant, PricingFlag.NORMAL);
        assertThat(quote.unitPrice()).isEqualByComparingTo("4500.00");
        assertThat(quote.source()).isEqualTo("VARIANT_FIELD");
        assertThat(quote.marginAmount()).isEqualByComparingTo("1700.00");
    }

    @Test
    void wholesaleFlagUsesWholesaleField() {
        PriceQuote quote = pricingService.quote(shopId, variant, PricingFlag.WHOLESALE);
        assertThat(quote.unitPrice()).isEqualByComparingTo("4000.00");
    }

    @Test
    void repairFlagUsesRepairField() {
        PriceQuote quote = pricingService.quote(shopId, variant, PricingFlag.REPAIR);
        assertThat(quote.unitPrice()).isEqualByComparingTo("4800.00");
    }

    @Test
    void missingWholesaleFallsBackToRetail() {
        variant.setWholesalePrice(null);
        PriceQuote quote = pricingService.quote(shopId, variant, PricingFlag.WHOLESALE);
        assertThat(quote.unitPrice()).isEqualByComparingTo("4500.00");
    }

    @Test
    void matchingRuleBeatsTheStickerPrice() {
        PriceRule rule = new PriceRule();
        rule.setId(UUID.randomUUID());
        rule.setShopId(shopId);
        rule.setName("Diwali 10% off");
        rule.setPriority(10);
        rule.setScopeType(PriceRule.ScopeType.ALL);
        rule.setPricingFlag(PricingFlag.NORMAL);
        rule.setStrategy(PriceRule.Strategy.PERCENT_OFF);
        rule.setBaseField(PriceRule.BaseField.RETAIL_PRICE);
        rule.setPercentage(new BigDecimal("10"));
        rule.setRespectMinPrice(true);

        when(priceRuleRepository.findActiveRules(eq(shopId), any(Instant.class))).thenReturn(List.of(rule));

        PriceQuote quote = pricingService.quote(shopId, variant, PricingFlag.NORMAL);
        assertThat(quote.unitPrice()).isEqualByComparingTo("4050.00");
        assertThat(quote.source()).isEqualTo("RULE");
        assertThat(quote.sourceLabel()).isEqualTo("Diwali 10% off");
        assertThat(quote.clampedToMinimum()).isFalse();
    }

    @Test
    void ruleCannotUndercutTheMinimum() {
        PriceRule rule = new PriceRule();
        rule.setId(UUID.randomUUID());
        rule.setShopId(shopId);
        rule.setName("Too generous");
        rule.setPriority(10);
        rule.setScopeType(PriceRule.ScopeType.ALL);
        rule.setStrategy(PriceRule.Strategy.FIXED_PRICE);
        rule.setBaseField(PriceRule.BaseField.RETAIL_PRICE);
        rule.setAmount(new BigDecimal("2000.00"));
        rule.setRespectMinPrice(true);

        when(priceRuleRepository.findActiveRules(eq(shopId), any(Instant.class))).thenReturn(List.of(rule));

        PriceQuote quote = pricingService.quote(shopId, variant, PricingFlag.NORMAL);
        assertThat(quote.unitPrice()).isEqualByComparingTo("3500.00");
        assertThat(quote.clampedToMinimum()).isTrue();
    }

    @Test
    void customerTypeIsPartOfTheMatch() {
        PriceRule wholesaleOnly = new PriceRule();
        wholesaleOnly.setId(UUID.randomUUID());
        wholesaleOnly.setName("Trade 5% extra");
        wholesaleOnly.setPriority(5);
        wholesaleOnly.setScopeType(PriceRule.ScopeType.ALL);
        wholesaleOnly.setCustomerType(CustomerType.WHOLESALE);
        wholesaleOnly.setStrategy(PriceRule.Strategy.PERCENT_OFF);
        wholesaleOnly.setBaseField(PriceRule.BaseField.WHOLESALE_PRICE);
        wholesaleOnly.setPercentage(new BigDecimal("5"));
        wholesaleOnly.setRespectMinPrice(true);

        when(priceRuleRepository.findActiveRules(eq(shopId), any(Instant.class)))
                .thenReturn(List.of(wholesaleOnly));

        PriceQuote retailBuyer = pricingService.quote(PriceContext.of(shopId, variant, PricingFlag.NORMAL));
        assertThat(retailBuyer.unitPrice()).isEqualByComparingTo("4500.00");

        PriceQuote tradeBuyer = pricingService.quote(
                PriceContext.of(shopId, variant, PricingFlag.WHOLESALE).withCustomerType(CustomerType.WHOLESALE));
        assertThat(tradeBuyer.unitPrice()).isEqualByComparingTo("3800.00");
        assertThat(tradeBuyer.sourceLabel()).isEqualTo("Trade 5% extra");
    }
}
