package com.fixflow.billing.web;

import com.fixflow.billing.domain.BillingOrder;
import com.fixflow.billing.service.BillingService;
import com.fixflow.billing.service.BillingService.BillingOverview;
import com.fixflow.billing.service.BillingService.CheckoutOrderResponse;
import com.fixflow.billing.service.BillingService.VerifyPaymentRequest;
import com.fixflow.admin.service.PlatformAdminService;
import com.fixflow.security.Authorize;
import com.fixflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
@Tag(name = "Billing")
public class BillingController {

    private final BillingService billingService;
    private final PlatformAdminService platformAdminService;

    @GetMapping
    @PreAuthorize(Authorize.WORKSPACE_BILLING)
    public BillingOverview overview() {
        return billingService.overview(CurrentUser.shopId());
    }

    @PostMapping("/orders")
    @PreAuthorize(Authorize.WORKSPACE_BILLING)
    public CheckoutOrderResponse create(@RequestBody Map<String, String> body) {
        String code = body.get("planCode") != null && !body.get("planCode").isBlank()
                ? body.get("planCode")
                : body.getOrDefault("priceCode", "COMPATIBILITY");
        return billingService.createOrder(CurrentUser.shopId(), code);
    }

    @PostMapping({"/verify", "/verify-payment"})
    @PreAuthorize(Authorize.WORKSPACE_BILLING)
    public BillingOrder verify(@RequestBody VerifyPaymentRequest body) {
        return billingService.verifyPayment(CurrentUser.shopId(), body);
    }

    @PostMapping("/orders/{id}/confirm")
    @PreAuthorize(Authorize.WORKSPACE_BILLING)
    public BillingOrder confirm(@PathVariable UUID id) {
        return billingService.confirm(CurrentUser.shopId(), id);
    }

    /**
     * Razorpay's own callback. Unauthenticated by necessity — Razorpay has no
     * session — so trust comes entirely from the signature over the raw body.
     */
    @PostMapping("/webhooks/razorpay")
    @SecurityRequirements
    public ResponseEntity<Void> razorpayWebhook(
            @RequestBody String rawBody,
            @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(name = "X-Razorpay-Event-Id", required = false) String eventId) {
        billingService.processRazorpayWebhook(rawBody, signature, eventId);
        return ResponseEntity.ok().build();
    }

    /**
     * Stand-in for the gateway callback when no Razorpay account is wired up.
     * Restricted to platform admins: as an ordinary authenticated endpoint, any
     * shopkeeper could post their own order id and mark it paid.
     */
    @PostMapping("/webhooks/dev")
    public BillingOrder webhook(@RequestBody Map<String, Object> body) {
        platformAdminService.requireAdmin();
        String eventId = String.valueOf(body.getOrDefault("eventId", UUID.randomUUID()));
        UUID orderId = UUID.fromString(String.valueOf(body.get("orderId")));
        String status = String.valueOf(body.getOrDefault("status", "CAPTURED"));
        return billingService.processWebhook("DEV", eventId, orderId, status, body);
    }
}
