package com.fixflow.billing.service;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fixflow.audit.service.AuditAction;
import com.fixflow.audit.service.AuditService;
import com.fixflow.billing.domain.BillingOrder;
import com.fixflow.billing.domain.BillingPrice;
import com.fixflow.billing.domain.BillingWebhookEvent;
import com.fixflow.billing.domain.WorkspaceEntitlement;
import com.fixflow.billing.domain.WorkspaceSubscription;
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
import com.fixflow.security.Permission;
import lombok.extern.slf4j.Slf4j;
import com.fixflow.shop.domain.Shop;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    public record PaymentReceipt(UUID id, String planName, String priceCode, java.math.BigDecimal amount,
                                 String currency, String status, Instant paidAt) {
    }

    public record SubscriptionCard(String planCode, String planName, String status, Instant periodEnd,
                                   List<String> features) {
    }

    public record ScreenCard(int included, int extra, int subscribed, int seats, int inUse,
                             boolean live, Instant periodEnd, java.math.BigDecimal amount,
                             java.math.BigDecimal renewAmount, String currency, String priceCode,
                             String interval) {
    }

    public record BillingOverview(List<PlanService.PlanCard> plans, SubscriptionCard subscription,
                                  List<PaymentReceipt> recentPayments, ScreenCard screens,
                                  String razorpayKeyId, boolean razorpayEnabled, boolean paymentRequired,
                                  boolean localActivationAvailable) {
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
    private final com.fixflow.notify.WorkspaceNotifier notifier;
    private final PlanService planService;
    private final ScreenSeatService screenSeatService;

    public static final String CATALOG = "CATALOG";
    public static final String SALES = "SALES";

    /** ₹50 activation: look up which parts fit a phone. */
    public static final List<String> CATALOG_PLAN = List.of("WORKSPACE_CREATE", CATALOG);

    /** ₹199 monthly: the full shop counter. */
    public static final List<String> OPERATIONAL = List.of(
            "WORKSPACE_CREATE", "MEMBER_ADD", "INVENTORY", SALES, "REPAIRS", "MULTI_USER", CATALOG);

    @Transactional(readOnly = true)
    public BillingOverview overview(UUID workspaceId) {
        boolean enabled = razorpayGateway.configured();
        var current = planService.currentPlan(workspaceId);
        var row = planService.subscription(workspaceId);
        SubscriptionCard subscription = current == null ? null
                : new SubscriptionCard(current.code(), current.name(),
                row == null ? WorkspaceSubscription.NONE : row.getStatus(),
                row == null ? null : row.getPeriodEnd(), current.features());
        var capacity = screenSeatService.capacity(workspaceId);
        var screenPrice = priceRepository.findByCodeAndActiveTrue(ScreenSeatService.EXTRA_SCREEN_PRICE)
                .orElse(null);
        java.math.BigDecimal unit = screenPrice == null ? java.math.BigDecimal.valueOf(50) : screenPrice.getAmount();
        int subscribed = capacity.subscribed();
        return new BillingOverview(
                planService.listSellable(),
                subscription,
                orderRepository.findTop3ByWorkspaceIdAndStatusOrderByCreatedAtDesc(
                                workspaceId, PaymentStatus.CAPTURED)
                        .stream()
                        .map(this::toReceipt)
                        .toList(),
                new ScreenCard(capacity.included(), capacity.extra(), subscribed, capacity.seats(), capacity.inUse(),
                        capacity.live(), capacity.periodEnd(), unit,
                        unit.multiply(java.math.BigDecimal.valueOf(Math.max(subscribed, 1))),
                        screenPrice == null ? "INR" : screenPrice.getCurrency(),
                        ScreenSeatService.EXTRA_SCREEN_PRICE, "MONTHLY"),
                enabled ? razorpayGateway.keyId() : null,
                enabled,
                paymentRequired(workspaceId),
                localActivationAvailable());
    }

    @Transactional
    public CheckoutOrderResponse createOrder(UUID workspaceId, String priceCode) {
        if ("WORKSPACE_JOIN".equals(priceCode)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Pay the join fee from My workspaces. Each shop join is a separate payment.");
        }
        boolean renewScreens = ScreenSeatService.EXTRA_SCREEN_RENEW.equals(priceCode);
        BillingPrice price = resolvePrice(renewScreens
                ? ScreenSeatService.EXTRA_SCREEN_PRICE
                : priceCode);
        java.math.BigDecimal amount = price.getAmount();
        String storedCode = price.getCode();
        String purpose = price.getCode();
        if (renewScreens) {
            Shop shop = shopRepository.findById(workspaceId)
                    .orElseThrow(() -> ApiException.notFound("Shop", workspaceId));
            if (shop.getExtraScreens() < 1) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "This shop has no extra screens to renew. Add one first.");
            }
            amount = price.getAmount().multiply(java.math.BigDecimal.valueOf(shop.getExtraScreens()));
            storedCode = ScreenSeatService.EXTRA_SCREEN_RENEW;
            purpose = ScreenSeatService.EXTRA_SCREEN_RENEW;
        }
        long amountPaise = RazorpayMoney.requireMinimum(RazorpayMoney.toPaise(amount));

        BillingOrder order = new BillingOrder();
        order.setWorkspaceId(workspaceId);
        order.setUserId(CurrentUser.userId());
        order.setPriceCode(storedCode);
        order.setPurpose(purpose);
        order.setAmount(amount);
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

    public void requireCatalog(UUID workspaceId) {
        if (!hasCatalog(workspaceId)) {
            throw new ApiException(ErrorCode.ENTITLEMENT_DENIED,
                    "This workspace needs an active plan that includes compatibility. Open Billing and pay.");
        }
    }

    /**
     * The single gate on growing a shop's roster: no account is created, invited,
     * or activated in a workspace that has not paid for team access.
     *
     * <p>Checked on every path that ends in a live membership rather than only on
     * the invite, because an invitation sent while paid could otherwise be
     * accepted weeks after the plan lapsed.
     */
    public void requireMemberSeat(UUID workspaceId) {
        if (!hasLive(workspaceId, "MEMBER_ADD")) {
            throw new ApiException(ErrorCode.ENTITLEMENT_DENIED,
                    "Adding people to this shop needs an active plan. "
                            + "Open Billing, complete payment, then add your team.");
        }
    }

    public boolean hasCatalog(UUID workspaceId) {
        return planService.hasFeature(workspaceId, "COMPATIBILITY")
                || hasLive(workspaceId, CATALOG)
                || hasLive(workspaceId, SALES);
    }

    public boolean hasFullShop(UUID workspaceId) {
        return planService.hasFeature(workspaceId, "SALES") || hasLive(workspaceId, SALES);
    }

    public boolean catalogOnly(UUID workspaceId) {
        return workspaceId != null && hasCatalog(workspaceId) && !hasFullShop(workspaceId)
                && !planService.hasFeature(workspaceId, "DASHBOARD");
    }

    public boolean paymentRequired(UUID workspaceId) {
        return workspaceId != null && !planService.hasLiveAccess(workspaceId);
    }

    public java.util.Set<String> features(UUID workspaceId) {
        return planService.featuresFor(workspaceId);
    }

    public PlanService.PlanCard currentPlan(UUID workspaceId) {
        return planService.currentPlan(workspaceId);
    }

    public WorkspaceSubscription subscription(UUID workspaceId) {
        return planService.subscription(workspaceId);
    }

    public static final String JOIN_PRICE = "WORKSPACE_JOIN";
    public static final String JOIN_USED = "JOIN_USED";

    public boolean hasUnspentJoinPayment(UUID userId, UUID workspaceId) {
        return findUnspentJoin(userId, workspaceId).isPresent();
    }

    /**
     * Marks one captured join payment as used. Returns false when the user still owes ₹50 for this shop.
     */
    public boolean consumeJoinPayment(UUID userId, UUID workspaceId) {
        if (userId != null && userRepository.findById(userId).map(u -> u.isSystemAdmin()).orElse(false)) {
            return true;
        }
        return findUnspentJoin(userId, workspaceId).map(order -> {
            order.setPurpose(JOIN_USED);
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);
            return true;
        }).orElse(false);
    }

    /**
     * Puts a consumed join payment back on file so the same person can request
     * this shop again without paying ₹50 a second time.
     */
    public void releaseJoinPayment(UUID userId, UUID workspaceId) {
        if (userId == null || workspaceId == null) {
            return;
        }
        orderRepository.findFirstByWorkspaceIdAndUserIdAndPriceCodeAndStatusAndPurposeOrderByCreatedAtDesc(
                        workspaceId, userId, JOIN_PRICE, PaymentStatus.CAPTURED, JOIN_USED)
                .ifPresent(order -> {
                    order.setPurpose(JOIN_PRICE);
                    order.setUpdatedAt(Instant.now());
                    orderRepository.save(order);
                });
    }

    public CheckoutOrderResponse createJoinOrder(UUID userId, UUID workspaceId) {
        BillingPrice price = priceRepository.findByCodeAndActiveTrue(JOIN_PRICE)
                .orElseThrow(() -> ApiException.notFound("Price", JOIN_PRICE));
        long amountPaise = RazorpayMoney.requireMinimum(RazorpayMoney.toPaise(price.getAmount()));

        BillingOrder order = new BillingOrder();
        order.setWorkspaceId(workspaceId);
        order.setUserId(userId);
        order.setPriceCode(price.getCode());
        order.setPurpose(JOIN_PRICE);
        order.setAmount(price.getAmount());
        order.setCurrency(price.getCurrency());
        order.setStatus(PaymentStatus.CREATED);
        order.setEntitlementCode(price.getEntitlement());

        if (razorpayGateway.configured()) {
            Map<String, String> notes = new LinkedHashMap<>();
            notes.put("workspace_id", workspaceId.toString());
            notes.put("price_code", price.getCode());
            notes.put("joiner_user_id", userId.toString());
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

    public BillingOrder verifyJoinPayment(UUID userId, VerifyPaymentRequest request) {
        if (request == null || isBlank(request.razorpayOrderId()) || isBlank(request.razorpayPaymentId())
                || isBlank(request.razorpaySignature())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "razorpay_order_id, razorpay_payment_id and razorpay_signature are required.");
        }
        BillingOrder order = orderRepository
                .findByGatewayOrderIdAndUserId(request.razorpayOrderId().trim(), userId)
                .orElseThrow(() -> ApiException.notFound("Billing order", request.razorpayOrderId()));
        if (!JOIN_PRICE.equals(order.getPriceCode())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "This payment is not a workspace join fee.");
        }
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

    public BillingOrder confirmJoinPayment(UUID userId, UUID orderId) {
        BillingOrder order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> ApiException.notFound("Billing order", orderId));
        if (!JOIN_PRICE.equals(order.getPriceCode())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "This payment is not a workspace join fee.");
        }
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

    private java.util.Optional<BillingOrder> findUnspentJoin(UUID userId, UUID workspaceId) {
        return orderRepository.findFirstByWorkspaceIdAndUserIdAndPriceCodeAndStatusAndPurposeOrderByCreatedAtDesc(
                workspaceId, userId, JOIN_PRICE, PaymentStatus.CAPTURED, JOIN_PRICE);
    }

    public Instant currentPeriodEnd(UUID workspaceId) {
        var row = planService.subscription(workspaceId);
        if (row != null && WorkspaceSubscription.ACTIVE.equals(row.getStatus())) {
            return row.getPeriodEnd();
        }
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
        planService.grantComplimentary(workspaceId, "FULL_SHOP");
    }

    public boolean localActivationAvailable() {
        return !production();
    }

    /**
     * Turns the full shop on without Razorpay. Local and demo only — production
     * must take a real payment.
     */
    @Transactional
    public void activateLocalShop(UUID workspaceId) {
        if (production()) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Local shop activation is not available in production.");
        }
        grantPilotEntitlements(workspaceId);
        log.info("Granted complimentary FULL_SHOP to workspace {} (local activation)", workspaceId);
    }

    private boolean production() {
        return environment.acceptsProfiles(Profiles.of("prod"));
    }

    @Transactional
    public void notifyPaymentPending(UUID workspaceId, UUID userId, String email) {
        notificationService.emit(workspaceId, userId, "PAYMENT_PENDING", email,
                "Finish opening your MobiStack shop",
                "Your shop is created. Open Billing, pick a plan, and pay so the features on that plan stay on.");
    }

    @Transactional
    public int sendDueReminders() {
        Instant now = Instant.now();
        int reminded = 0;
        java.util.Set<UUID> shops = new java.util.LinkedHashSet<>(
                planService.dueSoon(now, now.plus(7, ChronoUnit.DAYS)));
        shops.addAll(entitlementRepository.findWorkspaceIdsExpiringBetween(now, now.plus(7, ChronoUnit.DAYS)));
        for (UUID workspaceId : shops) {
            notifyOwner(workspaceId, "BILLING_REMINDER",
                    "Your MobiStack plan is due soon",
                    "Your current period ends soon. Open Billing and pay this month so the shop stays unlocked.");
            reminded++;
        }
        for (Shop shop : shopRepository.findByExtraScreensGreaterThanAndExtraScreensPeriodEndBetween(
                0, now, now.plus(7, ChronoUnit.DAYS))) {
            notifyOwner(shop.getId(), "BILLING_REMINDER",
                    "Extra screens are due soon",
                    "Pay ₹50 for each extra screen this month or those screens turn off.");
            reminded++;
        }
        return reminded;
    }

    @Transactional
    public int expireLapsedPlans() {
        Instant now = Instant.now();
        java.util.Set<UUID> shops = new java.util.LinkedHashSet<>(planService.lapseOverdue());
        for (WorkspaceEntitlement row : entitlementRepository.findByActiveTrueAndExpiresAtBefore(now)) {
            row.setActive(false);
            entitlementRepository.save(row);
            shops.add(row.getWorkspaceId());
        }
        shops.forEach(id -> notifyOwner(id, "PAYMENT_PENDING",
                "MobiStack payment is pending",
                "This month's payment was not completed. The shop is locked until you pay on Billing."));
        Instant justLapsedFrom = now.minus(1, ChronoUnit.DAYS);
        int extraOff = 0;
        for (Shop shop : shopRepository.findByExtraScreensGreaterThanAndExtraScreensPeriodEndBefore(0, now)) {
            if (shop.getExtraScreensPeriodEnd() != null && shop.getExtraScreensPeriodEnd().isAfter(justLapsedFrom)) {
                notifyOwner(shop.getId(), "PAYMENT_PENDING",
                        "Extra screens are off",
                        "This month's extra-screen payment was missed. Only the included screen stays on until you pay on Billing.");
                extraOff++;
            }
        }
        return shops.size() + extraOff;
    }

    /**
     * Accepts a payment result straight from Razorpay.
     *
     * <p>This is the only path that can be trusted when the shopkeeper's browser
     * never comes back — a closed tab after a successful UPI payment used to
     * leave the money taken and the plan still unpaid. The raw body is verified
     * against the webhook secret before anything is believed.
     *
     * @param rawBody   bytes exactly as received; re-serialised JSON will not verify
     * @param signature value of the {@code X-Razorpay-Signature} header
     * @param eventId   value of the {@code X-Razorpay-Event-Id} header, used for idempotency
     */
    @Transactional
    public BillingOrder processRazorpayWebhook(String rawBody, String signature, String eventId) {
        if (!razorpayGateway.webhooksConfigured()) {
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE,
                    "No Razorpay webhook secret is configured, so webhooks are refused.");
        }
        if (!razorpayGateway.verifyWebhookSignature(rawBody, signature)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Webhook signature did not match.");
        }
        Map<String, Object> payload = readJson(rawBody);
        String event = String.valueOf(payload.getOrDefault("event", ""));
        Map<String, Object> entity = paymentEntity(payload);
        String gatewayOrderId = text(entity.get("order_id"));
        if (gatewayOrderId == null) {
            // Razorpay sends account-level events we do not act on; acknowledging
            // them keeps it from retrying for ever.
            log.info("Ignoring Razorpay event {} with no order reference", event);
            return null;
        }
        BillingOrder order = orderRepository.findByGatewayOrderId(gatewayOrderId).orElse(null);
        if (order == null) {
            log.warn("Razorpay event {} referenced unknown order {}", event, gatewayOrderId);
            return null;
        }
        String status = event.endsWith(".failed") ? "FAILED" : "CAPTURED";
        String reference = eventId == null || eventId.isBlank()
                ? event + ":" + text(entity.get("id")) : eventId;
        return processWebhook("RAZORPAY", reference, order.getId(), status, payload);
    }

    private Map<String, Object> readJson(String rawBody) {
        try {
            return new tools.jackson.databind.ObjectMapper()
                    .readValue(rawBody, new tools.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "Webhook body was not readable JSON.");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> paymentEntity(Map<String, Object> payload) {
        Object body = payload.get("payload");
        if (!(body instanceof Map<?, ?> outer)) {
            return Map.of();
        }
        for (String kind : List.of("payment", "order")) {
            if (outer.get(kind) instanceof Map<?, ?> wrapper
                    && wrapper.get("entity") instanceof Map<?, ?> entity) {
                return (Map<String, Object>) entity;
            }
        }
        return Map.of();
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String asText = String.valueOf(value).trim();
        return asText.isEmpty() ? null : asText;
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
            orderRepository.save(order);
            notifyPaymentFailed(order);
            return order;
        }
        return order;
    }

    /**
     * A failed payment used to be silent, so a shop whose card was declined only
     * found out when the plan lapsed and the app stopped taking sales.
     */
    private void notifyPaymentFailed(BillingOrder order) {
        if (order.getWorkspaceId() == null) {
            return;
        }
        notifier.broadcast(order.getWorkspaceId(), Permission.WORKSPACE_BILLING, "PAYMENT_FAILED",
                "Payment did not go through",
                "The %s payment was declined. Open Billing to try again before the plan lapses."
                        .formatted(order.getPriceCode() == null ? "plan" : order.getPriceCode()),
                "/billing");
    }

    private BillingOrder markCaptured(BillingOrder order, String paymentId) {
        order.setStatus(PaymentStatus.CAPTURED);
        order.setGatewayPaymentId(paymentId);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        UUID workspaceId = order.getWorkspaceId();
        if (ScreenSeatService.EXTRA_SCREEN_RENEW.equals(order.getPriceCode())
                || ScreenSeatService.EXTRA_SCREEN_RENEW.equals(order.getPurpose())) {
            int extra = screenSeatService.renewExtraScreens(workspaceId);
            auditService.record(AuditAction.PAYMENT_CAPTURED, "BillingOrder", order.getId(),
                    "Renewed " + extra + " extra screens for the month");
            notifyOwner(workspaceId, "PAYMENT_RECEIVED",
                    "Extra screens paid this month",
                    extra + " extra screen" + (extra == 1 ? "" : "s")
                            + " stay on for this month. Pay again next month or those screens turn off.");
            return order;
        }
        if (ScreenSeatService.EXTRA_SCREEN_PRICE.equals(order.getPriceCode())) {
            int extra = screenSeatService.addExtraScreen(workspaceId);
            auditService.record(AuditAction.PAYMENT_CAPTURED, "BillingOrder", order.getId(),
                    "Added extra screen #" + extra + " (monthly)");
            notifyOwner(workspaceId, "PAYMENT_RECEIVED",
                    "Extra screen added",
                    "This shop can now have " + (1 + extra)
                            + " people signed in at the same time this month. Extra screens are ₹50 each per month.");
            return order;
        }
        Instant periodEnd = Instant.now().plus(31, ChronoUnit.DAYS);
        planService.activateFromOrder(order);
        if (JOIN_PRICE.equals(order.getPriceCode()) || "MEMBER_ADD".equals(order.getPriceCode())) {
            grantOne(workspaceId, order.getEntitlementCode(), periodEnd, order.getId());
        }
        auditService.record(AuditAction.PAYMENT_CAPTURED, "BillingOrder", order.getId(),
                "Captured billing order " + order.getPriceCode());
        notifyOwner(workspaceId, "PAYMENT_RECEIVED",
                "Payment received",
                "MobiStack recorded payment for " + order.getPriceCode().replace('_', ' ')
                        + ". This period stays on until " + periodEnd + ".");
        return order;
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

    private BillingPrice resolvePrice(String priceOrPlanCode) {
        if (priceOrPlanCode == null || priceOrPlanCode.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Pick a plan to pay.");
        }
        return priceRepository.findByCodeAndActiveTrue(priceOrPlanCode.trim())
                .or(() -> planService.listSellable().stream()
                        .filter(plan -> plan.code().equalsIgnoreCase(priceOrPlanCode.trim())
                                || plan.priceCode().equalsIgnoreCase(priceOrPlanCode.trim()))
                        .findFirst()
                        .flatMap(plan -> priceRepository.findByCodeAndActiveTrue(plan.priceCode())))
                .orElseThrow(() -> ApiException.notFound("Price", priceOrPlanCode));
    }

    private PaymentReceipt toReceipt(BillingOrder order) {
        String planName = ScreenSeatService.EXTRA_SCREEN_RENEW.equals(order.getPriceCode())
                ? "Extra screens (month)"
                : priceRepository.findByCode(order.getPriceCode())
                .map(price -> planService.planName(price.getPlanId()))
                .filter(name -> name != null && !name.isBlank())
                .orElse(order.getPriceCode().replace('_', ' '));
        return new PaymentReceipt(order.getId(), planName, order.getPriceCode(), order.getAmount(),
                order.getCurrency(), order.getStatus().name(),
                order.getUpdatedAt() != null ? order.getUpdatedAt() : order.getCreatedAt());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
