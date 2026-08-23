package com.fixflow.commerce.service;

import com.fixflow.audit.service.AuditService;
import com.fixflow.catalog.repository.ProductVariantRepository;
import com.fixflow.commerce.domain.Sale;
import com.fixflow.commerce.domain.SaleStatus;
import com.fixflow.commerce.dto.CommerceDtos.CreateSaleRequest;
import com.fixflow.commerce.dto.CommerceDtos.SaleLineRequest;
import com.fixflow.commerce.dto.CommerceDtos.SaleResponse;
import com.fixflow.commerce.repository.PaymentRepository;
import com.fixflow.commerce.repository.SaleItemRepository;
import com.fixflow.commerce.repository.SaleRepository;
import com.fixflow.inventory.service.InventoryService;
import com.fixflow.party.repository.CustomerRepository;
import com.fixflow.pricing.domain.PricingFlag;
import com.fixflow.pricing.service.PricingService;
import com.fixflow.shop.repository.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaleServiceTest {

    @Mock
    private SaleRepository saleRepository;
    @Mock
    private SaleItemRepository saleItemRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ProductVariantRepository variantRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private ShopRepository shopRepository;
    @Mock
    private PricingService pricingService;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private AuditService auditService;
    @Mock
    private com.fixflow.billing.service.BillingService billingService;

    private SaleService saleService;
    private UUID shopId;

    @BeforeEach
    void setUp() {
        saleService = new SaleService(saleRepository, saleItemRepository, paymentRepository, variantRepository,
                customerRepository, shopRepository, pricingService, inventoryService, auditService,
                new com.fixflow.config.FixFlowProperties(), billingService);
        shopId = UUID.randomUUID();
    }

    @Test
    void completeReturnsTheExistingSaleWhenTheIdempotencyKeyMatches() {
        Sale existing = new Sale();
        existing.setId(UUID.randomUUID());
        existing.setShopId(shopId);
        existing.setInvoiceNumber("INV000001");
        existing.setStatus(SaleStatus.COMPLETED);
        existing.setPricingFlag(PricingFlag.NORMAL);
        existing.setSubtotal(new BigDecimal("80"));
        existing.setDiscount(BigDecimal.ZERO);
        existing.setTax(BigDecimal.ZERO);
        existing.setTotal(new BigDecimal("80"));
        existing.setPaid(new BigDecimal("80"));
        existing.setOutstanding(BigDecimal.ZERO);
        existing.setProfit(new BigDecimal("20"));
        existing.setOccurredAt(Instant.now());

        when(saleRepository.findByShopIdAndIdempotencyKey(shopId, "pos-1")).thenReturn(Optional.of(existing));
        when(saleItemRepository.findBySaleIdOrderByCreatedAtAsc(existing.getId())).thenReturn(List.of());
        when(paymentRepository.findByShopIdAndReferenceTypeAndReferenceIdOrderByOccurredAtAsc(any(), any(), any()))
                .thenReturn(List.of());

        CreateSaleRequest request = new CreateSaleRequest(null, PricingFlag.NORMAL, BigDecimal.ZERO, null, "pos-1",
                null, List.of(new SaleLineRequest(UUID.randomUUID(), 1, new BigDecimal("80"), BigDecimal.ZERO)),
                List.of());

        SaleResponse response = saleService.complete(shopId, request);

        assertThat(response.id()).isEqualTo(existing.getId());
        assertThat(response.invoiceNumber()).isEqualTo("INV000001");
        verify(inventoryService, never()).post(any(), any());
    }
}
