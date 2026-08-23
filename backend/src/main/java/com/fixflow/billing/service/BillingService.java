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
import com.fixflow.notify.NotificationService;
import com.fixflow.security.CurrentUser;
import com.fixflow.shop.repository.ShopRepository;
import com.fixflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
                                  String razorpayKeyId, boolean razorpayEnabled, boolean paymentRequired,
                                  Instant currentPeriodEnd) {
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
    private final NotificationService notificationService;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final com.fixflow.notify.MailGateway mailGateway;

    public static final List<String> OPERATIONAL = List.of(
            "WORKSPACE_CREATE", "MEMBER_ADD", "INVENTORY", "SALES", "REPAIRS", "MULTI_USER");

    @Transactional(readOnly = true)
    public BillingOverview overview(UUID workspaceId) {
        boolean enabled = razorpayGateway.configured();
        return new BillingOverview(
                priceRepository.findByActiveTrue().stream()
                        .map(p -> new PriceCard(p.getCode(), p.getAmount(), p.getCurrency(), p.getInterval(),
                                p.getEntitlement()))
                        .toList(),
                entitlementRepository.findByWorkspaceIdAndActiveTrue(workspaceId).stream()
                        .filter(this::live)
                        .map(WorkspaceEntitlement::getCode).toList(),
                orderRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId),
                enabled ? razorpayGateway.keyId() : null,
                enabled,
                paymentRequired(workspaceId),
                currentPeriodEnd(workspaceId));
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
        if (!hasLive(workspaceId, entitlement)) {
            throw new ApiException(ErrorCode.ENTITLEMENT_DENIED,
                    "This workspace needs an active MobiStack plan. Open Billing and complete payment.");
        }
    }

    public boolean paymentRequired(UUID workspaceId) {
        return workspaceId != null && !hasLive(workspaceId, "SALES");
    }

    public Instant currentPeriodEnd(UUID workspaceId) {
        return entitlementRepository.findByWorkspaceIdAndActiveTrue(workspaceId).stream()
                .filter(this::live)
                .map(WorkspaceEntitlement::getExpiresAt)
                .filter(value -> value != null)
                .max(Instant::compareTo)
                .orElse(null);
    }

    public boolean hasLive(UUID workspaceId, String entitlement) {
        return entitlementRepository.findByWorkspaceIdAndCode(workspaceId, entitlement)
                .filter(this::live)
                .isPresent();
    }

    @Transactional
    public void grantPilotEntitlements(UUID workspaceId) {
        grantOperational(workspaceId, null, null);
    }

    @Transactional
    public void notifyPaymentPending(UUID workspaceId, UUID userId, String email) {
        notificationService.emit(workspaceId, userId, "PAYMENT_PENDING", email,
                "Finish opening your MobiStack shop",
                "Your shop is created. Pay the activation or monthly plan on Billing to unlock sales, repairs, and stock.");
    }

    @Transactional
    public int sendDueReminders() {
        Instant now = Instant.now();
        int reminded = 0;
        for (UUID workspaceId : entitlementRepository.findWorkspaceIdsExpiringBetween(now, now.plus(7, ChronoUnit.DAYS))) {
            notifyOwner(workspaceId, "BILLING_REMINDER",
                    "Your MobiStack plan is due soon",
                    "Your current period ends soon. Open Billing and pay the monthly plan so the counter stays unlocked.");
            reminded++;
        }
        return reminded;
    }

    @Transactional
    public int expireLapsedPlans() {
        Instant now = Instant.now();
        java.util.Set<UUID> shops = new java.util.LinkedHashSet<>();
        for (WorkspaceEntitlement row : entitlementRepository.findByActiveTrueAndExpiresAtBefore(now)) {
            row.setActive(false);
            entitlementRepository.save(row);
            shops.add(row.getWorkspaceId());
        }
        shops.forEach(id -> notifyOwner(id, "PAYMENT_PENDING",
                "MobiStack payment is pending",
                "The shop plan has lapsed. Pay the monthly plan on Billing to restore sales, repairs, and stock."));
        return shops.size();
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
        Instant periodEnd = Instant.now().plus(31, ChronoUnit.DAYS);
        if ("WORKSPACE_ACTIVATION".equals(order.getPriceCode()) || "WORKSPACE_MONTHLY".equals(order.getPriceCode())) {
            grantOperational(workspaceId, periodEnd, order.getId());
        } else {
            grantOne(workspaceId, order.getEntitlementCode(), periodEnd, order.getId());
        }
        auditService.record(AuditAction.PAYMENT_CAPTURED, "BillingOrder", order.getId(),
                "Captured billing order " + order.getPriceCode());
        notifyOwner(workspaceId, "PAYMENT_RECEIVED",
                "Payment received",
                "MobiStack recorded payment for " + order.getPriceCode().replace('_', ' ')
                        + ". The shop counter is unlocked until "
                        + periodEnd.toString() + ".");
        return order;
    }

    private void grantOperational(UUID workspaceId, Instant expiresAt, UUID orderId) {
        OPERATIONAL.forEach(code -> grantOne(workspaceId, code, expiresAt, orderId));
    }

    private void grantOne(UUID workspaceId, String code, Instant expiresAt, UUID orderId) {
        WorkspaceEntitlement row = entitlementRepository.findByWorkspaceIdAndCode(workspaceId, code)
                .orElseGet(WorkspaceEntitlement::new);
        row.setWorkspaceId(workspaceId);
        row.setCode(code);
        row.setActive(true);
        row.setSourceOrderId(orderId);
        if (expiresAt != null) {
            row.setExpiresAt(expiresAt);
        }
        entitlementRepository.save(row);
    }

    private boolean live(WorkspaceEntitlement row) {
        return row != null && row.isActive()
                && (row.getExpiresAt() == null || row.getExpiresAt().isAfter(Instant.now()));
    }

    private void notifyOwner(UUID workspaceId, String event, String subject, String body) {
        shopRepository.findById(workspaceId).ifPresent(shop -> {
            var user = userRepository.findWithRolesByEmail(shop.getEmail()).orElse(null);
            UUID userId = user == null ? null : user.getId();
            String email = shop.getEmail() != null ? shop.getEmail() : (user == null ? null : user.getEmail());
            notificationService.emit(workspaceId, userId, event, email, subject, body);
            if (email != null && email.contains("@") && mailGateway.configured()) {
                try {
                    mailGateway.send(email, subject, body);
                } catch (RuntimeException ignored) {
                    /* inbox already has the reminder */
                }
            }
        });
    }

    private static String receiptFor(UUID orderId) {
        String compact = "ms" + orderId.toString().replace("-", "");
        return compact.length() <= 40 ? compact : compact.substring(0, 40);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
