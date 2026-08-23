package com.fixflow.billing.service;

import com.fixflow.audit.service.AuditAction;
import com.fixflow.audit.service.AuditService;
import com.fixflow.billing.domain.BillingOrder;
import com.fixflow.billing.domain.BillingPrice;
import com.fixflow.billing.domain.BillingWebhookEvent;
import com.fixflow.billing.domain.WorkspaceEntitlement;
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
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingService {

    public record PriceCard(String code, java.math.BigDecimal amount, String currency, String interval,
                            String entitlement) {
    }

    public record BillingOverview(List<PriceCard> prices, List<String> entitlements, List<BillingOrder> orders) {
    }

    private final BillingPriceRepository priceRepository;
    private final BillingOrderRepository orderRepository;
    private final WorkspaceEntitlementRepository entitlementRepository;
    private final BillingWebhookEventRepository webhookEventRepository;
    private final AuditService auditService;
    private final Environment environment;

    @Transactional(readOnly = true)
    public BillingOverview overview(UUID workspaceId) {
        return new BillingOverview(
                priceRepository.findByActiveTrue().stream()
                        .map(p -> new PriceCard(p.getCode(), p.getAmount(), p.getCurrency(), p.getInterval(),
                                p.getEntitlement()))
                        .toList(),
                entitlementRepository.findByWorkspaceIdAndActiveTrue(workspaceId).stream()
                        .map(WorkspaceEntitlement::getCode).toList(),
                orderRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId));
    }

    @Transactional
    public BillingOrder createOrder(UUID workspaceId, String priceCode) {
        BillingPrice price = priceRepository.findByCodeAndActiveTrue(priceCode)
                .orElseThrow(() -> ApiException.notFound("Price", priceCode));
        BillingOrder order = new BillingOrder();
        order.setWorkspaceId(workspaceId);
        order.setUserId(CurrentUser.userId());
        order.setPriceCode(price.getCode());
        order.setPurpose(price.getCode());
        order.setAmount(price.getAmount());
        order.setCurrency(price.getCurrency());
        order.setStatus(PaymentStatus.CREATED);
        order.setGateway("DEV");
        order.setGatewayOrderId("dev-" + UUID.randomUUID());
        order.setEntitlementCode(price.getEntitlement());
        orderRepository.save(order);
        auditService.record(AuditAction.PAYMENT_CAPTURED, "BillingOrder", order.getId(),
                "Created billing order " + priceCode);
        return order;
    }

    /**
     * Local capture only. Production must confirm through a signed payment webhook.
     */
    @Transactional
    public BillingOrder confirm(UUID workspaceId, UUID orderId) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE,
                    "Self-confirm is disabled in production. Complete payment through the configured gateway.");
        }
        BillingOrder order = orderRepository.findByIdAndWorkspaceId(orderId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Billing order", orderId));
        if (order.getStatus() == PaymentStatus.CAPTURED) {
            return order;
        }
        order.setStatus(PaymentStatus.CAPTURED);
        order.setGatewayPaymentId("dev-pay-" + UUID.randomUUID());
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        if (!entitlementRepository.existsByWorkspaceIdAndCodeAndActiveTrue(workspaceId, order.getEntitlementCode())) {
            WorkspaceEntitlement entitlement = new WorkspaceEntitlement();
            entitlement.setWorkspaceId(workspaceId);
            entitlement.setCode(order.getEntitlementCode());
            entitlement.setSourceOrderId(order.getId());
            entitlementRepository.save(entitlement);
        }
        return order;
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
                                       java.util.Map<String, Object> payload) {
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
        event.setPayload(payload == null ? java.util.Map.of() : payload);
        event.setProcessedAt(Instant.now());
        webhookEventRepository.save(event);
        BillingOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> ApiException.notFound("Billing order", orderId));
        if ("CAPTURED".equalsIgnoreCase(status) || "paid".equalsIgnoreCase(status)) {
            return confirm(order.getWorkspaceId(), order.getId());
        }
        if ("FAILED".equalsIgnoreCase(status)) {
            order.setStatus(PaymentStatus.FAILED);
            order.setUpdatedAt(Instant.now());
            return orderRepository.save(order);
        }
        return order;
    }
}
