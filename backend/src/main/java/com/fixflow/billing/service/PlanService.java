package com.fixflow.billing.service;

import com.fixflow.billing.domain.BillingOrder;
import com.fixflow.billing.domain.BillingPlan;
import com.fixflow.billing.domain.BillingPrice;
import com.fixflow.billing.domain.PlanCatalog;
import com.fixflow.billing.domain.WorkspaceEntitlement;
import com.fixflow.billing.domain.WorkspaceSubscription;
import com.fixflow.billing.repository.BillingPlanRepository;
import com.fixflow.billing.repository.BillingPriceRepository;
import com.fixflow.billing.repository.WorkspaceEntitlementRepository;
import com.fixflow.billing.repository.WorkspaceSubscriptionRepository;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PlanService {

    public record FeatureCard(String code, String label, String help) {
    }

    public record PlanCard(UUID id, String code, String name, String description, BigDecimal amount,
                           String currency, String interval, int sortOrder, boolean active,
                           List<String> features, String priceCode) {
    }

    public record PlanWrite(String code, String name, String description, BigDecimal amount,
                            String currency, String interval, Integer sortOrder, Boolean active,
                            List<String> features) {
    }

    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,38}$");
    private static final Set<String> HIDDEN_PRICE_CODES = Set.of(
            "WORKSPACE_JOIN", "MEMBER_ADD", "EXTRA_SCREEN", "EXTRA_SCREEN_RENEW");

    private final BillingPlanRepository planRepository;
    private final BillingPriceRepository priceRepository;
    private final WorkspaceSubscriptionRepository subscriptionRepository;
    private final WorkspaceEntitlementRepository entitlementRepository;

    public List<FeatureCard> catalog() {
        return PlanCatalog.ALL.stream()
                .map(item -> new FeatureCard(item.code(), item.label(), item.help()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlanCard> listAll() {
        return planRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(this::toCard)
                .toList();
    }

    /** The counter plan stays in the catalog. It is not offered for sale while the shop is closed. */
    public static final String WITHHELD_PLAN = "FULL_SHOP";

    @Transactional(readOnly = true)
    public List<PlanCard> listSellable() {
        return planRepository.findByActiveTrueOrderBySortOrderAscNameAsc().stream()
                .filter(plan -> !plan.getFeatures().isEmpty())
                .filter(plan -> !WITHHELD_PLAN.equals(plan.getCode()))
                .map(this::toCard)
                .toList();
    }

    @Transactional
    public PlanCard create(PlanWrite request) {
        String code = normalizeCode(request.code(), request.name());
        if (planRepository.existsByCode(code) || priceRepository.findByCode(code).isPresent()) {
            throw ApiException.conflict("A plan with this code already exists.");
        }
        BillingPlan plan = new BillingPlan();
        apply(plan, request, code);
        planRepository.save(plan);
        syncPrice(plan, code);
        return toCard(plan);
    }

    @Transactional
    public PlanCard update(UUID id, PlanWrite request) {
        BillingPlan plan = planRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Plan", id));
        apply(plan, request, plan.getCode());
        planRepository.save(plan);
        syncPrice(plan, checkoutCode(plan));
        return toCard(plan);
    }

    @Transactional(readOnly = true)
    public WorkspaceSubscription subscription(UUID workspaceId) {
        return subscriptionRepository.findById(workspaceId).orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean hasLiveAccess(UUID workspaceId) {
        return liveSubscription(workspaceId) != null;
    }

    @Transactional(readOnly = true)
    public Set<String> featuresFor(UUID workspaceId) {
        WorkspaceSubscription row = liveSubscription(workspaceId);
        if (row == null || row.getPlanId() == null) {
            return Set.of();
        }
        return planRepository.findById(row.getPlanId())
                .map(plan -> Set.copyOf(plan.getFeatures()))
                .orElse(Set.of());
    }

    @Transactional(readOnly = true)
    public boolean hasFeature(UUID workspaceId, String feature) {
        return featuresFor(workspaceId).contains(feature);
    }

    @Transactional(readOnly = true)
    public PlanCard currentPlan(UUID workspaceId) {
        WorkspaceSubscription row = liveSubscription(workspaceId);
        if (row == null || row.getPlanId() == null) {
            return null;
        }
        return planRepository.findById(row.getPlanId()).map(this::toCard).orElse(null);
    }

    @Transactional
    public void activateFromOrder(BillingOrder order) {
        if (order == null || "WORKSPACE_JOIN".equals(order.getPriceCode())
                || "EXTRA_SCREEN".equals(order.getPriceCode())
                || "EXTRA_SCREEN_RENEW".equals(order.getPriceCode())) {
            return;
        }
        BillingPlan plan = planForPrice(order.getPriceCode());
        if (plan == null || plan.getFeatures().isEmpty()) {
            return;
        }
        Instant periodEnd = Instant.now().plus(31, ChronoUnit.DAYS);
        activate(order.getWorkspaceId(), plan, periodEnd, order.getId());
    }

    @Transactional
    public void grantComplimentary(UUID workspaceId, String planCode) {
        BillingPlan plan = planRepository.findByCode(planCode)
                .orElseThrow(() -> ApiException.notFound("Plan", planCode));
        activate(workspaceId, plan, null, null);
    }

    @Transactional
    public void assign(UUID workspaceId, UUID planId, boolean complimentary) {
        BillingPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> ApiException.notFound("Plan", planId));
        Instant periodEnd = complimentary ? null : Instant.now().plus(31, ChronoUnit.DAYS);
        activate(workspaceId, plan, periodEnd, null);
    }

    @Transactional
    public List<UUID> lapseOverdue() {
        Instant now = Instant.now();
        List<UUID> shops = new java.util.ArrayList<>();
        for (WorkspaceSubscription row : subscriptionRepository.findByStatusAndPeriodEndBefore(
                WorkspaceSubscription.ACTIVE, now)) {
            row.setStatus(WorkspaceSubscription.PAST_DUE);
            row.setUpdatedAt(now);
            subscriptionRepository.save(row);
            revokeOperational(row.getWorkspaceId());
            shops.add(row.getWorkspaceId());
        }
        return shops;
    }

    @Transactional(readOnly = true)
    public String planName(UUID planId) {
        if (planId == null) {
            return null;
        }
        return planRepository.findById(planId).map(BillingPlan::getName).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<UUID> dueSoon(Instant now, Instant until) {
        return subscriptionRepository.findByStatusAndPeriodEndBetween(WorkspaceSubscription.ACTIVE, now, until)
                .stream()
                .map(WorkspaceSubscription::getWorkspaceId)
                .toList();
    }

    public String checkoutCode(BillingPlan plan) {
        return priceRepository.findByPlanIdAndActiveTrue(plan.getId()).stream()
                .filter(price -> !HIDDEN_PRICE_CODES.contains(price.getCode()))
                .map(BillingPrice::getCode)
                .findFirst()
                .orElse(plan.getCode());
    }

    private WorkspaceSubscription liveSubscription(UUID workspaceId) {
        if (workspaceId == null) {
            return null;
        }
        WorkspaceSubscription row = subscriptionRepository.findById(workspaceId).orElse(null);
        if (row == null || !WorkspaceSubscription.ACTIVE.equals(row.getStatus())) {
            return null;
        }
        if (row.getPeriodEnd() != null && !row.getPeriodEnd().isAfter(Instant.now())) {
            return null;
        }
        return row;
    }

    private void activate(UUID workspaceId, BillingPlan plan, Instant periodEnd, UUID orderId) {
        WorkspaceSubscription row = subscriptionRepository.findById(workspaceId).orElseGet(WorkspaceSubscription::new);
        row.setWorkspaceId(workspaceId);
        row.setPlanId(plan.getId());
        row.setStatus(WorkspaceSubscription.ACTIVE);
        row.setPeriodEnd(periodEnd);
        row.setSourceOrderId(orderId);
        row.setUpdatedAt(Instant.now());
        subscriptionRepository.save(row);
        applyEntitlements(workspaceId, plan.getFeatures(), periodEnd, orderId);
    }

    private void applyEntitlements(UUID workspaceId, Set<String> features, Instant expiresAt, UUID orderId) {
        Set<String> wanted = new LinkedHashSet<>(PlanCatalog.entitlementsFor(features));
        for (String code : wanted) {
            grantOne(workspaceId, code, expiresAt, orderId, true);
        }
        for (WorkspaceEntitlement row : entitlementRepository.findByWorkspaceIdAndActiveTrue(workspaceId)) {
            if (!wanted.contains(row.getCode())) {
                row.setActive(false);
                entitlementRepository.save(row);
            }
        }
    }

    private void revokeOperational(UUID workspaceId) {
        for (WorkspaceEntitlement row : entitlementRepository.findByWorkspaceIdAndActiveTrue(workspaceId)) {
            if ("WORKSPACE_CREATE".equals(row.getCode())) {
                continue;
            }
            row.setActive(false);
            entitlementRepository.save(row);
        }
    }

    private void grantOne(UUID workspaceId, String code, Instant expiresAt, UUID orderId, boolean active) {
        WorkspaceEntitlement row = entitlementRepository.findByWorkspaceIdAndCode(workspaceId, code)
                .orElseGet(WorkspaceEntitlement::new);
        row.setWorkspaceId(workspaceId);
        row.setCode(code);
        row.setActive(active);
        row.setSourceOrderId(orderId);
        row.setExpiresAt(expiresAt);
        entitlementRepository.save(row);
    }

    private BillingPlan planForPrice(String priceCode) {
        if (priceCode == null) {
            return null;
        }
        return priceRepository.findByCode(priceCode)
                .flatMap(price -> planRepository.findById(price.getPlanId()))
                .or(() -> planRepository.findByCode(priceCode))
                .orElse(null);
    }

    private void apply(BillingPlan plan, PlanWrite request, String code) {
        plan.setCode(code);
        plan.setName(requireText(request.name(), "name"));
        plan.setDescription(trimTo(request.description(), 255));
        if (request.amount() != null) {
            if (request.amount().signum() < 0) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Amount cannot be negative.");
            }
            plan.setAmount(request.amount());
        }
        if (request.currency() != null && !request.currency().isBlank()) {
            plan.setCurrency(request.currency().trim().toUpperCase(Locale.ROOT));
        }
        String interval = request.interval() == null || request.interval().isBlank()
                ? "MONTHLY" : request.interval().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ONE_TIME", "MONTHLY", "ANNUAL").contains(interval)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Interval must be MONTHLY, ANNUAL, or ONE_TIME.");
        }
        plan.setInterval(interval);
        if (request.sortOrder() != null) {
            plan.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            plan.setActive(request.active());
        }
        Set<String> features = new LinkedHashSet<>();
        if (request.features() != null) {
            for (String feature : request.features()) {
                if (feature == null || feature.isBlank()) {
                    continue;
                }
                String normalized = feature.trim().toUpperCase(Locale.ROOT);
                if (!PlanCatalog.known(normalized)) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown feature: " + feature);
                }
                features.add(normalized);
            }
        }
        plan.setFeatures(features);
    }

    private void syncPrice(BillingPlan plan, String priceCode) {
        BillingPrice price = priceRepository.findByCode(priceCode).orElseGet(BillingPrice::new);
        if (price.getId() == null) {
            price.setId(UUID.randomUUID());
            price.setCode(priceCode);
        }
        price.setPlanId(plan.getId());
        price.setAmount(plan.getAmount());
        price.setCurrency(plan.getCurrency());
        price.setInterval(plan.getInterval());
        price.setEntitlement("PLAN");
        price.setActive(plan.isActive());
        priceRepository.save(price);
    }

    private PlanCard toCard(BillingPlan plan) {
        return new PlanCard(plan.getId(), plan.getCode(), plan.getName(), plan.getDescription(),
                plan.getAmount(), plan.getCurrency(), plan.getInterval(), plan.getSortOrder(),
                plan.isActive(), List.copyOf(plan.getFeatures()), checkoutCode(plan));
    }

    private static String normalizeCode(String raw, String name) {
        String source = raw == null || raw.isBlank() ? name : raw;
        if (source == null || source.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Plan code is required.");
        }
        String code = source.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("_+", "_").replaceAll("^_|_$", "");
        if (!CODE.matcher(code).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Plan code must start with a letter and use A–Z, 0–9, or underscore.");
        }
        return code;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, field + " is required.");
        }
        return value.trim();
    }

    private static String trimTo(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
