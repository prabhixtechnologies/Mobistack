package com.fixflow.admin.web;

import com.fixflow.admin.service.PlatformAdminService;
import com.fixflow.admin.service.PlatformAdminService.WorkspaceAdminCard;
import com.fixflow.billing.service.ScreenSeatService;
import com.fixflow.billing.repository.BillingOrderRepository;
import com.fixflow.billing.service.PlanService;
import com.fixflow.commons.domain.CatalogContribution;
import com.fixflow.commons.service.ContributionService;
import com.fixflow.flags.service.FeatureFlagService;
import com.fixflow.flags.service.FeatureFlagService.FlagCard;
import com.fixflow.presence.PresenceService;
import com.fixflow.presence.PresenceSnapshot;
import com.fixflow.shop.domain.Shop;
import com.fixflow.shop.repository.ShopRepository;
import com.fixflow.support.service.SupportService;
import com.fixflow.updates.service.AppReleaseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fixflow.commerce.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Platform admin")
public class PlatformAdminController {

    private final PlatformAdminService platformAdminService;
    private final FeatureFlagService featureFlagService;
    private final BillingOrderRepository billingOrderRepository;
    private final PresenceService presenceService;
    private final ScreenSeatService screenSeatService;
    private final SupportService supportService;
    private final AppReleaseService appReleaseService;
    private final ShopRepository shopRepository;
    private final PlanService planService;
    private final ContributionService contributionService;

    @GetMapping("/workspaces")
    public List<WorkspaceAdminCard> workspaces() {
        platformAdminService.requireAdmin();
        return platformAdminService.listWorkspaces();
    }

    @PostMapping("/workspaces/{id}/suspend")
    public Shop suspend(@PathVariable UUID id) {
        platformAdminService.requireAdmin();
        return platformAdminService.setActive(id, false);
    }

    @PostMapping("/workspaces/{id}/activate")
    public Shop activate(@PathVariable UUID id) {
        platformAdminService.requireAdmin();
        return platformAdminService.setActive(id, true);
    }

    @GetMapping("/feature-flags")
    public List<FlagCard> flags() {
        platformAdminService.requireAdmin();
        return featureFlagService.resolved(null);
    }

    @PutMapping("/feature-flags")
    public FlagCard upsertFlag(@RequestBody Map<String, Object> body) {
        platformAdminService.requireAdmin();
        UUID shopId = body.get("shopId") == null ? null : UUID.fromString(String.valueOf(body.get("shopId")));
        return featureFlagService.upsert(shopId, String.valueOf(body.get("code")),
                Boolean.parseBoolean(String.valueOf(body.getOrDefault("enabled", false))));
    }

    @GetMapping("/billing/orders")
    public List<Map<String, Object>> orders() {
        platformAdminService.requireAdmin();
        return billingOrderRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 200)).stream()
                .map(order -> {
                    var shop = shopRepository.findById(order.getWorkspaceId()).orElse(null);
                    return Map.<String, Object>ofEntries(
                            Map.entry("id", order.getId()),
                            Map.entry("shopId", order.getWorkspaceId()),
                            Map.entry("shopName", shop == null ? "Unknown shop" : shop.getName()),
                            Map.entry("priceCode", order.getPriceCode()),
                            Map.entry("amount", order.getAmount()),
                            Map.entry("currency", order.getCurrency()),
                            Map.entry("status", order.getStatus().name()),
                            Map.entry("gateway", order.getGateway()),
                            Map.entry("createdAt", order.getCreatedAt()),
                            Map.entry("paidAt", order.getUpdatedAt())
                    );
                })
                .toList();
    }

    /**
     * Platform-wide payment rollup for Admin Overview / Commerce KPIs.
     * Amounts are major currency units (rupees), matching {@code /billing/orders}.
     */
    @GetMapping("/billing/revenue")
    public Map<String, Object> revenue() {
        platformAdminService.requireAdmin();
        EnumMap<PaymentStatus, BigDecimal> totals = new EnumMap<>(PaymentStatus.class);
        EnumMap<PaymentStatus, Long> counts = new EnumMap<>(PaymentStatus.class);
        for (PaymentStatus status : PaymentStatus.values()) {
            totals.put(status, BigDecimal.ZERO);
            counts.put(status, 0L);
        }
        for (Object[] row : billingOrderRepository.aggregateByStatus()) {
            PaymentStatus status = (PaymentStatus) row[0];
            BigDecimal sum = row[1] instanceof BigDecimal bd ? bd : BigDecimal.valueOf(((Number) row[1]).doubleValue());
            long count = ((Number) row[2]).longValue();
            totals.put(status, sum);
            counts.put(status, count);
        }
        BigDecimal captured = totals.getOrDefault(PaymentStatus.CAPTURED, BigDecimal.ZERO);
        long capturedCount = counts.getOrDefault(PaymentStatus.CAPTURED, 0L);
        BigDecimal pending = totals.getOrDefault(PaymentStatus.CREATED, BigDecimal.ZERO)
                .add(totals.getOrDefault(PaymentStatus.PENDING, BigDecimal.ZERO))
                .add(totals.getOrDefault(PaymentStatus.AUTHORIZED, BigDecimal.ZERO));
        long pendingCount = counts.getOrDefault(PaymentStatus.CREATED, 0L)
                + counts.getOrDefault(PaymentStatus.PENDING, 0L)
                + counts.getOrDefault(PaymentStatus.AUTHORIZED, 0L);
        long failedCount = counts.getOrDefault(PaymentStatus.FAILED, 0L)
                + counts.getOrDefault(PaymentStatus.EXPIRED, 0L)
                + counts.getOrDefault(PaymentStatus.REFUNDED, 0L)
                + counts.getOrDefault(PaymentStatus.PARTIALLY_REFUNDED, 0L);
        return Map.of(
                "capturedTotal", captured,
                "pendingTotal", pending,
                "capturedCount", capturedCount,
                "pendingCount", pendingCount,
                "failedCount", failedCount,
                "currency", "INR",
                "asOf", Instant.now().toString()
        );
    }

    @GetMapping("/plan-features")
    public List<PlanService.FeatureCard> planFeatures() {
        platformAdminService.requireAdmin();
        return planService.catalog();
    }

    @GetMapping("/plans")
    public List<PlanService.PlanCard> plans() {
        platformAdminService.requireAdmin();
        return planService.listAll();
    }

    @PostMapping("/plans")
    public PlanService.PlanCard createPlan(@RequestBody PlanService.PlanWrite body) {
        platformAdminService.requireAdmin();
        return planService.create(body);
    }

    @PutMapping("/plans/{id}")
    public PlanService.PlanCard updatePlan(@PathVariable UUID id, @RequestBody PlanService.PlanWrite body) {
        platformAdminService.requireAdmin();
        return planService.update(id, body);
    }

    @PostMapping("/workspaces/{id}/plan")
    public Map<String, Object> assignPlan(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        platformAdminService.requireAdmin();
        UUID planId = UUID.fromString(String.valueOf(body.get("planId")));
        boolean complimentary = Boolean.parseBoolean(String.valueOf(body.getOrDefault("complimentary", false)));
        planService.assign(id, planId, complimentary);
        return Map.of("assigned", true);
    }

    @GetMapping("/live")
    public List<PresenceSnapshot> live() {
        platformAdminService.requireAdmin();
        return presenceService.liveAll();
    }

    @PostMapping("/live/{userId}/kick")
    public Map<String, Object> kick(@PathVariable UUID userId,
                                    @RequestBody(required = false) Map<String, String> body) {
        UUID actor = platformAdminService.requireAdmin();
        String deviceId = body == null ? null : body.get("deviceId");
        int kicked = presenceService.kick(userId, deviceId);
        return Map.of("kicked", kicked, "userId", userId, "actor", actor);
    }

    @PostMapping({"/workspaces/{id}/screens", "/workspaces/{id}/device-limit"})
    public Shop deviceLimit(@PathVariable UUID id, @RequestBody Map<String, Integer> body) {
        platformAdminService.requireAdmin();
        Integer extra = body.get("extraScreens");
        if (extra == null && body.get("maxDevicesPerUser") != null) {
            extra = Math.max(0, body.get("maxDevicesPerUser") - 1);
        }
        screenSeatService.setExtraScreens(id, extra == null ? 0 : extra);
        return shopRepository.findById(id).orElseThrow();
    }

    @GetMapping("/support")
    public List<SupportService.ConversationCard> support(@org.springframework.web.bind.annotation.RequestParam(required = false) String status) {
        platformAdminService.requireAdmin();
        return supportService.adminList(status);
    }

    @PostMapping("/support/{id}/messages")
    public SupportService.ConversationCard supportReply(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        platformAdminService.requireAdmin();
        return supportService.adminReply(id, body.get("message"));
    }

    @PostMapping("/support/{id}/resolve")
    public SupportService.ConversationCard supportResolve(@PathVariable UUID id) {
        platformAdminService.requireAdmin();
        return supportService.resolve(id);
    }

    @GetMapping("/app-releases")
    public List<AppReleaseService.ReleasePolicy> releases() {
        platformAdminService.requireAdmin();
        return appReleaseService.all();
    }

    @PutMapping("/app-releases/{platform}")
    public AppReleaseService.ReleasePolicy updateRelease(@PathVariable String platform,
                                                         @RequestBody AppReleaseService.ReleaseUpdate body) {
        platformAdminService.requireAdmin();
        return appReleaseService.update(platform, body);
    }

    @GetMapping("/commons/queue")
    public Page<ContributionCard> commonsQueue(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        platformAdminService.requireAdmin();
        return contributionService.queue(page, size).map(ContributionCard::of);
    }

    @PostMapping("/commons/{id}/accept")
    public ContributionCard commonsAccept(@PathVariable UUID id,
                                          @RequestBody(required = false) Map<String, String> body) {
        UUID actor = platformAdminService.requireAdmin();
        String note = body == null ? null : body.get("note");
        return ContributionCard.of(contributionService.accept(actor, id, note));
    }

    @PostMapping("/commons/{id}/reject")
    public ContributionCard commonsReject(@PathVariable UUID id,
                                          @RequestBody(required = false) Map<String, String> body) {
        UUID actor = platformAdminService.requireAdmin();
        String note = body == null ? null : body.get("note");
        return ContributionCard.of(contributionService.reject(actor, id, note));
    }

    public record ContributionCard(UUID id,
                                   CatalogContribution.Kind kind,
                                   CatalogContribution.Status status,
                                   UUID targetId,
                                   UUID appliedId,
                                   UUID submittedBy,
                                   String reason,
                                   String reviewNote,
                                   Instant createdAt,
                                   Instant reviewedAt) {
        static ContributionCard of(CatalogContribution contribution) {
            return new ContributionCard(contribution.getId(), contribution.getKind(),
                    contribution.getStatus(), contribution.getTargetId(), contribution.getAppliedId(),
                    contribution.getSubmittedBy(), contribution.getReason(), contribution.getReviewNote(),
                    contribution.getCreatedAt(), contribution.getReviewedAt());
        }
    }
}
