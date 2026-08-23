package com.fixflow.inventory.service;

import com.fixflow.audit.service.AuditService;
import com.fixflow.catalog.domain.Product;
import com.fixflow.catalog.domain.ProductVariant;
import com.fixflow.catalog.repository.ProductVariantRepository;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import com.fixflow.inventory.domain.InventoryTransaction;
import com.fixflow.inventory.domain.InventoryTransactionType;
import com.fixflow.inventory.repository.InventoryTransactionRepository;
import com.fixflow.inventory.repository.StockAlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private ProductVariantRepository variantRepository;
    @Mock
    private InventoryTransactionRepository transactionRepository;
    @Mock
    private StockAlertRepository stockAlertRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private com.fixflow.billing.service.BillingService billingService;

    private InventoryService inventoryService;
    private UUID shopId;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(variantRepository, transactionRepository,
                stockAlertRepository, auditService, new FixFlowProperties(), billingService);
        shopId = UUID.randomUUID();

        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setName("Realme 6 Display");
        product.setCategoryId(UUID.randomUUID());

        variant = new ProductVariant();
        variant.setId(UUID.randomUUID());
        variant.setShopId(shopId);
        variant.setProduct(product);
        variant.setVariantName("Realme 6 Display / GX");
        variant.setOnHandQty(2);
        variant.setReservedQty(0);
        variant.setReorderLevel(3);
        variant.setCostPrice(new BigDecimal("2800"));
        variant.setRetailPrice(new BigDecimal("4500"));
    }

    @Test
    void receiveIncreasesOnHandAndWritesALedgerRow() {
        when(variantRepository.findForStockUpdate(variant.getId(), shopId)).thenReturn(Optional.of(variant));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockAlertRepository.findOpen(any(), any())).thenReturn(Optional.empty());

        InventoryTransaction txn = inventoryService.receive(shopId, variant.getId(), 5,
                new BigDecimal("2700"), "Courier arrived", "B-19");

        assertThat(txn.getType()).isEqualTo(InventoryTransactionType.IN);
        assertThat(txn.getOnHandDelta()).isEqualTo(5);
        assertThat(txn.getBalanceAfter()).isEqualTo(7);
        assertThat(variant.getOnHandQty()).isEqualTo(7);
        assertThat(variant.getCostPrice()).isEqualByComparingTo("2700");
    }

    @Test
    void cannotIssueMoreThanIsOnHand() {
        when(variantRepository.findForStockUpdate(variant.getId(), shopId)).thenReturn(Optional.of(variant));

        assertThatThrownBy(() -> inventoryService.issue(shopId, variant.getId(), 5,
                com.fixflow.inventory.domain.InventoryReferenceType.SALE, UUID.randomUUID(), "INV-1"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
    }

    @Test
    void replayedIdempotencyKeyDoesNotMoveStockTwice() {
        InventoryTransaction existing = new InventoryTransaction();
        existing.setId(UUID.randomUUID());
        existing.setIdempotencyKey("tablet-sale-42");
        when(transactionRepository.findByShopIdAndIdempotencyKey(shopId, "tablet-sale-42"))
                .thenReturn(Optional.of(existing));

        InventoryTransaction result = inventoryService.post(shopId,
                StockMovement.of(variant.getId(), InventoryTransactionType.OUT, 1)
                        .idempotencyKey("tablet-sale-42")
                        .build());

        assertThat(result.getId()).isEqualTo(existing.getId());
        verify(variantRepository, never()).findForStockUpdate(any(), any());
        assertThat(variant.getOnHandQty()).isEqualTo(2);
    }

    @Test
    void stockTakeRecordsTheDifferenceNotTheAbsoluteCount() {
        when(variantRepository.findByIdAndShopId(variant.getId(), shopId)).thenReturn(Optional.of(variant));
        when(variantRepository.findForStockUpdate(variant.getId(), shopId)).thenReturn(Optional.of(variant));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockAlertRepository.findOpen(any(), any())).thenReturn(Optional.empty());

        inventoryService.adjustTo(shopId, variant.getId(), 5, "Found three more in the drawer");

        ArgumentCaptor<InventoryTransaction> captor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(InventoryTransactionType.ADJUSTMENT);
        assertThat(captor.getValue().getOnHandDelta()).isEqualTo(3);
        assertThat(variant.getOnHandQty()).isEqualTo(5);
    }
}
