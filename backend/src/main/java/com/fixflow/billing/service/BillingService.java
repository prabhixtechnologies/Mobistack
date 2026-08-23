package com.fixflow.billing.service;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fixflow.audit.service.AuditAction;
import com.fixflow.audit.service.AuditService;
import com.fixflow.billing.domain.BillingOrder;
import com.fixflow.billing.domain.BillingPrice;
import com.fixflow.billing.domain.BillingWebhookEvent;
import com.fixflow.billing.domain.WorkspaceEntitlement;
import com.fixflow.billing.razorpay.RazorpayGateway;
import com.fixflow.billing.razorpay.RazorpayMoney;
import com.fixflow.billing.repository.BillingOrderRepository;
import com.fixflow.billing.repository.BillingPriceRepository;
import com.fixflow.billing.repository.BillingWebhookEventRepository;
import com.fixflow.billing.repository.WorkspaceEntitlementRepository;
import com.fixflow.commerce.domain.PaymentStatus;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingService {

    public record PriceCard(String code, java.math.BigDecimal amount, String currency, String interval,
                            String entitlement) {
    }

    public record BillingOverview(List<PriceCard> prices, List<String> entitlements, List<BillingOrder> orders,
                                  String razorpayKeyId, boolean razorpayEnabled) {
    }

    public record CheckoutOrderResponse(
            UUID id,
            @JsonProperty("order_id") String orderId,
            long amount,
            String currency,
            String keyId,
            String priceCode,
            String gateway
    ) {
    }

    public record VerifyPaymentRequest(
            @JsonAlias({"razorpay_order_id", "order_id"}) String razorpayOrderId,
            @JsonAlias({"razorpay_payment_id", "payment_id"}) String razorpayPaymentId,
            @JsonAlias({"razorpay_signature", "signature"}) String razorpaySignature
    ) {
    }

    private final BillingPriceRepository priceRepository;
    private final BillingOrderRepository orderRepository;
    private final WorkspaceEntitlementRepository entitlementRepository;
    private final BillingWebhookEventRepository webhookEventRepository;
    private final AuditService auditService;
    private final Environment environment;
    private final RazorpayGateway razorpayGateway;

    @Transactional(readOnly = true)
    public BillingOverview overview(UUID workspaceId) {
        boolean enabled = razorpayGateway.configured();
        return new BillingOverview(
                priceRepository.findByActiveTrue().stream()
                        .map(p -> new PriceCard(p.getCode(), p.getAmount(), p.getCurrency(), p.getInterval(),
                                p.getEntitlement()))
                        .toList(),
                entitlementRepository.findByWorkspaceIdAndActiveTrue(workspaceId).stream()
                        .map(WorkspaceEntitlement::getCode).toList(),
                orderRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId),
                enabled ? razorpayGateway.keyId() : null,
                enabled);
    }

    @Transactional
    public CheckoutOrderResponse createOrder(UUID workspaceId, String priceCode) {
        BillingPrice price = priceRepository.findByCodeAndActiveTrue(priceCode)
                .orElseThrow(() -> ApiException.notFound("Price", priceCode));
        long amountPaise = RazorpayMoney.requireMinimum(RazorpayMoney.toPaise(price.getAmount()));

        BillingOrder order = new BillingOrder();
        order.setWorkspaceId(workspaceId);
        order.setUserId(CurrentUser.userId());
        order.setPriceCode(price.getCode());
        order.setPurpose(price.getCode());
        order.setAmount(price.getAmount());
        order.setCurrency(price.getCurrency());
        order.setStatus(PaymentStatus.CREATED);
        order.setEntitlementCode(price.getEntitlement());

        if (razorpayGateway.configured()) {
            Map<String, String> notes = new LinkedHashMap<>();
            notes.put("workspace_id", workspaceId.toString());
            notes.put("price_code", price.getCode());
            notes.put("internal_order_id", order.getId().toString());
            RazorpayGateway.CreatedOrder remote = razorpayGateway.createOrder(
                    amountPaise, price.getCurrency(), receiptFor(order.getId()), notes);
            order.setGateway("RAZORPAY");
            order.setGatewayOrderId(remote.id());
            order.setStatus(PaymentStatus.PENDING);
        } else if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE,
                    "Razorpay is not configured. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET.");
        } else {
            order.setGateway("DEV");
            order.setGatewayOrderId("dev-" + UUID.randomUUID());
        }
        orderRepository.save(order);
        return new CheckoutOrderResponse(order.getId(), order.getGatewayOrderId(), amountPaise,
                order.getCurrency(), razorpayGateway.configured() ? razorpayGateway.keyId() : null,
                order.getPriceCode(), order.getGateway());
    }

    /**
     * Razorpay Standard Checkout handler. Marks paid only when the HMAC matches.
     */
    @Transactional
    public BillingOrder verifyPayment(UUID workspaceId, VerifyPaymentRequest request) {
        if (request == null || isBlank(request.razorpayOrderId()) || isBlank(request.razorpayPaymentId())
                || isBlank(request.razorpaySignature())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "razorpay_order_id, razorpay_payment_id and razorpay_signature are required.");
        }
        BillingOrder order = orderRepository
                .findByGatewayOrderIdAndWorkspaceId(request.razorpayOrderId().trim(), workspaceId)
                .orElseThrow(() -> ApiException.notFound("Billing order", request.razorpayOrderId()));
        if (order.getStatus() == PaymentStatus.CAPTURED) {
            return order;
        }
        if (!razorpayGateway.verifyCheckoutSignature(request.razorpayOrderId().trim(),
                request.razorpayPaymentId().trim(), request.razorpaySignature().trim())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Payment signature did not match. The order was not marked as paid.");
        }
        return markCaptured(order, request.razorpayPaymentId().trim());
    }

    /**
     * Local capture only, and only for DEV gateway orders. Razorpay orders must use verifyPayment.
     */
    @Transactional
    public BillingOrder confirm(UUID workspaceId, UUID orderId) {
        BillingOrder order = orderRepository.findByIdAndWorkspaceId(orderId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Billing order", orderId));
        if ("RAZORPAY".equalsIgnoreCase(order.getGateway())) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Complete this order through Razorpay Checkout. Self-confirm is not allowed.");
        }
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE,
                    "Self-confirm is disabled in production. Complete payment through Razorpay.");
        }
        if (order.getStatus() == PaymentStatus.CAPTURED) {
            return order;
        }
        return markCaptured(order, "dev-pay-" + UUID.randomUUID());
    }

    public void require(UUID workspaceId, String entitlement) {
        if (!entitlementRepository.existsByWorkspaceIdAndCodeAndActiveTrue(workspaceId, entitlement)) {
            throw new ApiException(ErrorCode.ENTITLEMENT_DENIED,
                    "This workspace does not have the " + entitlement + " entitlement.");
        }
    }

    @Transactional
    public void grantPilotEntitlements(UUID workspaceId) {
        for (String code : List.of("WORKSPACE_CREATE", "MEMBER_ADD", "INVENTORY", "SALES", "REPAIRS", "MULTI_USER")) {
            WorkspaceEntitlement row = entitlementRepository.findByWorkspaceIdAndCode(workspaceId, code)
                    .orElseGet(WorkspaceEntitlement::new);
            row.setWorkspaceId(workspaceId);
            row.setCode(code);
            row.setActive(true);
            entitlementRepository.save(row);
        }
    }

    @Transactional
    public BillingOrder processWebhook(String provider, String eventId, UUID orderId, String status,
                                       Map<String, Object> payload) {
        if (environment.acceptsProfiles(Profiles.of("prod")) && "DEV".equalsIgnoreCase(provider)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "The development billing webhook is disabled in production.");
        }
        var existing = webhookEventRepository.findByProviderAndEventId(provider, eventId);
        if (existing.isPresent() && existing.get().getProcessedAt() != null) {
            return orderRepository.findById(orderId).orElse(null);
        }
        BillingWebhookEvent event = existing.orElseGet(BillingWebhookEvent::new);
        event.setProvider(provider);
        event.setEventId(eventId);
        event.setPayload(payload == null ? Map.of() : payload);
        event.setProcessedAt(Instant.now());
        webhookEventRepository.save(event);
        BillingOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> ApiException.notFound("Billing order", orderId));
        if ("CAPTURED".equalsIgnoreCase(status) || "paid".equalsIgnoreCase(status)) {
            if (order.getStatus() == PaymentStatus.CAPTURED) {
                return order;
            }
            return markCaptured(order, order.getGatewayPaymentId() == null
                    ? "webhook-" + eventId : order.getGatewayPaymentId());
        }
        if ("FAILED".equalsIgnoreCase(status)) {
            order.setStatus(PaymentStatus.FAILED);
            order.setUpdatedAt(Instant.now());
            return orderRepository.save(order);
        }
        return order;
    }

    private BillingOrder markCaptured(BillingOrder order, String paymentId) {
        order.setStatus(PaymentStatus.CAPTURED);
        order.setGatewayPaymentId(paymentId);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        UUID workspaceId = order.getWorkspaceId();
        if (!entitlementRepository.existsByWorkspaceIdAndCodeAndActiveTrue(workspaceId, order.getEntitlementCode())) {
            WorkspaceEntitlement entitlement = new WorkspaceEntitlement();
            entitlement.setWorkspaceId(workspaceId);
            entitlement.setCode(order.getEntitlementCode());
            entitlement.setSourceOrderId(order.getId());
            entitlementRepository.save(entitlement);
        }
        auditService.record(AuditAction.PAYMENT_CAPTURED, "BillingOrder", order.getId(),
                "Captured billing order " + order.getPriceCode());
        return order;
    }

    private static String receiptFor(UUID orderId) {
        String compact = "ms" + orderId.toString().replace("-", "");
        return compact.length() <= 40 ? compact : compact.substring(0, 40);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
