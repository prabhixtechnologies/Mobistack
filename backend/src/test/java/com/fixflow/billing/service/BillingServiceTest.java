package com.fixflow.billing.service;

import com.fixflow.audit.service.AuditService;
import com.fixflow.billing.domain.BillingOrder;
import com.fixflow.billing.razorpay.RazorpayGateway;
import com.fixflow.billing.repository.BillingOrderRepository;
import com.fixflow.billing.repository.BillingPriceRepository;
import com.fixflow.billing.repository.BillingWebhookEventRepository;
import com.fixflow.billing.repository.WorkspaceEntitlementRepository;
import com.fixflow.commerce.domain.PaymentStatus;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

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
class BillingServiceTest {

    @Mock
    private BillingPriceRepository priceRepository;
    @Mock
    private BillingOrderRepository orderRepository;
    @Mock
    private WorkspaceEntitlementRepository entitlementRepository;
    @Mock
    private BillingWebhookEventRepository webhookEventRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private Environment environment;
    @Mock
    private RazorpayGateway razorpayGateway;

    private BillingService billingService;

    @BeforeEach
    void setUp() {
        billingService = new BillingService(priceRepository, orderRepository, entitlementRepository,
                webhookEventRepository, auditService, environment, razorpayGateway);
    }

    @Test
    void verifyRejectsMissingFields() {
        UUID workspaceId = UUID.randomUUID();
        assertThatThrownBy(() -> billingService.verifyPayment(workspaceId,
                new BillingService.VerifyPaymentRequest(null, "pay_1", "sig")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void verifyDoesNotCaptureOnBadSignature() {
        UUID workspaceId = UUID.randomUUID();
        BillingOrder order = pendingRazorpayOrder(workspaceId, "order_1");
        when(orderRepository.findByGatewayOrderIdAndWorkspaceId("order_1", workspaceId))
                .thenReturn(Optional.of(order));
        when(razorpayGateway.verifyCheckoutSignature("order_1", "pay_1", "bad-sig")).thenReturn(false);

        assertThatThrownBy(() -> billingService.verifyPayment(workspaceId,
                new BillingService.VerifyPaymentRequest("order_1", "pay_1", "bad-sig")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(order.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(entitlementRepository, never()).save(any());
    }

    @Test
    void verifyCapturesOnMatchingSignature() {
        UUID workspaceId = UUID.randomUUID();
        BillingOrder order = pendingRazorpayOrder(workspaceId, "order_1");
        when(orderRepository.findByGatewayOrderIdAndWorkspaceId("order_1", workspaceId))
                .thenReturn(Optional.of(order));
        when(razorpayGateway.verifyCheckoutSignature("order_1", "pay_1", "good-sig")).thenReturn(true);
        when(entitlementRepository.existsByWorkspaceIdAndCodeAndActiveTrue(workspaceId, "WORKSPACE_CREATE"))
                .thenReturn(false);

        BillingOrder captured = billingService.verifyPayment(workspaceId,
                new BillingService.VerifyPaymentRequest("order_1", "pay_1", "good-sig"));

        assertThat(captured.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(captured.getGatewayPaymentId()).isEqualTo("pay_1");
        verify(entitlementRepository).save(any());
    }

    private static BillingOrder pendingRazorpayOrder(UUID workspaceId, String gatewayOrderId) {
        BillingOrder order = new BillingOrder();
        order.setWorkspaceId(workspaceId);
        order.setUserId(UUID.randomUUID());
        order.setPriceCode("WORKSPACE_ACTIVATION");
        order.setPurpose("WORKSPACE_ACTIVATION");
        order.setAmount(new BigDecimal("50.00"));
        order.setCurrency("INR");
        order.setStatus(PaymentStatus.PENDING);
        order.setGateway("RAZORPAY");
        order.setGatewayOrderId(gatewayOrderId);
        order.setEntitlementCode("WORKSPACE_CREATE");
        return order;
    }
}
