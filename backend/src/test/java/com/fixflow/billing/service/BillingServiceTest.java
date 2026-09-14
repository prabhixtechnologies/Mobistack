package com.fixflow.billing.service;

import tools.jackson.databind.ObjectMapper;
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
import com.fixflow.notify.MailGateway;
import com.fixflow.notify.NotificationService;
import com.fixflow.shop.repository.ShopRepository;
import com.fixflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

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
    @Mock
    private NotificationService notificationService;
    @Mock
    private ShopRepository shopRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MailGateway mailGateway;
    @Mock
    private com.fixflow.notify.WorkspaceNotifier notifier;
    @Mock
    private PlanService planService;
    @Mock
    private ScreenSeatService screenSeatService;

    private BillingService billingService;

    @BeforeEach
    void setUp() {
        billingService = new BillingService(priceRepository, orderRepository, entitlementRepository,
                webhookEventRepository, auditService, environment, razorpayGateway, notificationService,
                shopRepository, userRepository, mailGateway, notifier, planService, screenSeatService);
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

        BillingOrder captured = billingService.verifyPayment(workspaceId,
                new BillingService.VerifyPaymentRequest("order_1", "pay_1", "good-sig"));

        assertThat(captured.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(captured.getGatewayPaymentId()).isEqualTo("pay_1");
        verify(planService).activateFromOrder(captured);
        verify(screenSeatService, never()).addExtraScreen(any());
    }

    @Test
    void extraScreenPaymentAddsASeatWithoutChangingThePlan() {
        UUID workspaceId = UUID.randomUUID();
        BillingOrder order = pendingRazorpayOrder(workspaceId, "order_screen");
        order.setPriceCode("EXTRA_SCREEN");
        order.setPurpose("EXTRA_SCREEN");
        when(orderRepository.findByGatewayOrderIdAndWorkspaceId("order_screen", workspaceId))
                .thenReturn(Optional.of(order));
        when(razorpayGateway.verifyCheckoutSignature("order_screen", "pay_1", "good-sig")).thenReturn(true);
        when(screenSeatService.addExtraScreen(workspaceId)).thenReturn(1);

        BillingOrder captured = billingService.verifyPayment(workspaceId,
                new BillingService.VerifyPaymentRequest("order_screen", "pay_1", "good-sig"));

        assertThat(captured.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        verify(screenSeatService).addExtraScreen(workspaceId);
        verify(planService, never()).activateFromOrder(any());
    }

    @Test
    void extraScreenRenewalExtendsTheMonthWithoutAddingASeat() {
        UUID workspaceId = UUID.randomUUID();
        BillingOrder order = pendingRazorpayOrder(workspaceId, "order_renew");
        order.setPriceCode("EXTRA_SCREEN_RENEW");
        order.setPurpose("EXTRA_SCREEN_RENEW");
        when(orderRepository.findByGatewayOrderIdAndWorkspaceId("order_renew", workspaceId))
                .thenReturn(Optional.of(order));
        when(razorpayGateway.verifyCheckoutSignature("order_renew", "pay_1", "good-sig")).thenReturn(true);
        when(screenSeatService.renewExtraScreens(workspaceId)).thenReturn(2);

        BillingOrder captured = billingService.verifyPayment(workspaceId,
                new BillingService.VerifyPaymentRequest("order_renew", "pay_1", "good-sig"));

        assertThat(captured.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        verify(screenSeatService).renewExtraScreens(workspaceId);
        verify(screenSeatService, never()).addExtraScreen(any());
        verify(planService, never()).activateFromOrder(any());
    }

    @Test
    void requireBlocksWhenSalesEntitlementIsMissing() {
        UUID workspaceId = UUID.randomUUID();
        when(entitlementRepository.findByWorkspaceIdAndCode(any(), any()))
                .thenReturn(Optional.empty());
        when(planService.hasLiveAccess(workspaceId)).thenReturn(false);
        when(planService.hasFeature(any(), any())).thenReturn(false);

        assertThat(billingService.paymentRequired(workspaceId)).isTrue();
        assertThat(billingService.catalogOnly(workspaceId)).isFalse();
        assertThatThrownBy(() -> billingService.require(workspaceId, "SALES"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.ENTITLEMENT_DENIED);
    }

    @Test
    void catalogPlanUnlocksLookupWithoutTheFullShop() {
        UUID workspaceId = UUID.randomUUID();
        when(entitlementRepository.findByWorkspaceIdAndCode(workspaceId, "SALES"))
                .thenReturn(Optional.empty());
        when(planService.hasLiveAccess(workspaceId)).thenReturn(true);
        when(planService.hasFeature(workspaceId, "COMPATIBILITY")).thenReturn(true);
        when(planService.hasFeature(workspaceId, "SALES")).thenReturn(false);
        when(planService.hasFeature(workspaceId, "DASHBOARD")).thenReturn(false);

        assertThat(billingService.paymentRequired(workspaceId)).isFalse();
        assertThat(billingService.catalogOnly(workspaceId)).isTrue();
        assertThatThrownBy(() -> billingService.require(workspaceId, "SALES"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void activateLocalShopRejectedInProduction() {
        when(environment.acceptsProfiles(Profiles.of("prod"))).thenReturn(true);

        assertThatThrownBy(() -> billingService.activateLocalShop(UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        verify(planService, never()).grantComplimentary(any(), any());
    }

    @Test
    void activateLocalShopGrantsFullShopOutsideProduction() {
        UUID workspaceId = UUID.randomUUID();
        when(environment.acceptsProfiles(Profiles.of("prod"))).thenReturn(false);

        billingService.activateLocalShop(workspaceId);

        verify(planService).grantComplimentary(workspaceId, "FULL_SHOP");
    }

    @Test
    void checkoutOrderJsonExposesRazorpayOrderId() throws Exception {
        String json = new ObjectMapper().writeValueAsString(new BillingService.CheckoutOrderResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "order_test123", 5000L, "INR", "rzp_test_key", "WORKSPACE_ACTIVATION", "RAZORPAY"));
        assertThat(json).contains("\"order_id\":\"order_test123\"");
        assertThat(json).contains("\"keyId\":\"rzp_test_key\"");
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
