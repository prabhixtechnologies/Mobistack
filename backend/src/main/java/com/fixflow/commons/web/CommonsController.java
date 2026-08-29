package com.fixflow.commons.web;

import com.fixflow.commons.domain.CatalogContribution;
import com.fixflow.commons.domain.CatalogContribution.Kind;
import com.fixflow.commons.domain.CatalogContributor;
import com.fixflow.commons.domain.CatalogEntities.CatalogBrand;
import com.fixflow.commons.domain.CatalogEntities.CatalogDevice;
import com.fixflow.commons.service.CommonsCatalogService;
import com.fixflow.commons.service.ContributionService;
import com.fixflow.security.Authorize;
import com.fixflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The shared compatibility catalog, readable by anyone signed in.
 *
 * <p>Every other business endpoint in this service requires a selected workspace, enforced by
 * {@code WorkspaceGuardFilter}. These do not, and that is the point: someone can look up what fits
 * before they have created a shop, and a shop with an unpaid plan can still contribute. The guard
 * exempts this prefix explicitly.
 *
 * <p>Authorization here is {@code isAuthenticated()} rather than a shop permission, because a shop
 * permission is granted by a workspace and this is not a workspace's data. Reviewing is the exception:
 * it changes what every other shop reads.
 */
@RestController
@RequestMapping("/api/v1/commons")
@RequiredArgsConstructor
@Tag(name = "Compatibility commons")
public class CommonsController {

    private final CommonsCatalogService catalog;
    private final ContributionService contributions;

    // --- Reading -----------------------------------------------------------------------------------

    @GetMapping("/brands")
    @PreAuthorize("isAuthenticated()")
    public List<BrandView> brands() {
        return catalog.listBrands().stream().map(BrandView::of).toList();
    }

    @GetMapping("/devices")
    @PreAuthorize("isAuthenticated()")
    public Page<DeviceView> devices(@RequestParam String q,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return catalog.searchDevices(q, page, size).map(DeviceView::of);
    }

    @GetMapping("/components")
    @PreAuthorize("isAuthenticated()")
    public Page<ComponentView> components(@RequestParam String q,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return catalog.searchComponents(q, page, size).map(ComponentView::of);
    }

    /** What fits this phone. The question the commons exists to answer. */
    @GetMapping("/devices/{deviceId}/fits")
    @PreAuthorize("isAuthenticated()")
    public List<FitView> fits(@PathVariable UUID deviceId) {
        List<FitView> answer = catalog.fitmentsForDevice(deviceId).stream()
                .map(FitView::of)
                .toList();
        catalog.recordLookup(deviceId);
        return answer;
    }

    /** What this part fits — the other direction, for someone holding stock. */
    @GetMapping("/components/{componentId}/devices")
    @PreAuthorize("isAuthenticated()")
    public List<DeviceView> devicesFor(@PathVariable UUID componentId) {
        return catalog.devicesForComponent(componentId).stream().map(DeviceView::of).toList();
    }

    // --- Contributing ------------------------------------------------------------------------------

    @PostMapping("/contributions")
    @PreAuthorize("isAuthenticated()")
    public ContributionView contribute(@Valid @RequestBody ContributionRequest request) {
        CatalogContribution saved = contributions.submit(
                CurrentUser.userId(),
                request.kind(),
                request.targetId(),
                request.payload(),
                request.reason());
        return ContributionView.of(saved);
    }

    @GetMapping("/contributions/mine")
    @PreAuthorize("isAuthenticated()")
    public Page<ContributionView> mine(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return contributions.mine(CurrentUser.userId(), page, size).map(ContributionView::of);
    }

    @GetMapping("/standing")
    @PreAuthorize("isAuthenticated()")
    public StandingView standing() {
        return StandingView.of(contributions.standingOf(CurrentUser.userId()));
    }

    // --- Reviewing ---------------------------------------------------------------------------------

    @GetMapping("/review/queue")
    @PreAuthorize(Authorize.COMMONS_REVIEW)
    public Page<ContributionView> queue(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return contributions.queue(page, size).map(ContributionView::of);
    }

    @PostMapping("/review/{id}/accept")
    @PreAuthorize(Authorize.COMMONS_REVIEW)
    public ContributionView accept(@PathVariable UUID id, @RequestBody(required = false) ReviewNote note) {
        return ContributionView.of(
                contributions.accept(CurrentUser.userId(), id, note == null ? null : note.note()));
    }

    @PostMapping("/review/{id}/reject")
    @PreAuthorize(Authorize.COMMONS_REVIEW)
    public ContributionView reject(@PathVariable UUID id, @RequestBody(required = false) ReviewNote note) {
        return ContributionView.of(
                contributions.reject(CurrentUser.userId(), id, note == null ? null : note.note()));
    }

    /**
     * Settles a dispute for good, rather than waiting for the crowd to outvote it.
     *
     * <p>Distinct from a confirmation: confirmations are a crowd count, and this is somebody with the
     * authority to say the argument is over.
     */
    @PostMapping("/review/fitments/{fitmentId}/verify")
    @PreAuthorize(Authorize.COMMONS_REVIEW)
    public VerifiedView verify(@PathVariable UUID fitmentId) {
        var fitment = catalog.markVerified(fitmentId, CurrentUser.userId());
        return new VerifiedView(fitment.getId(), fitment.getComponentId(), fitment.getDeviceId(),
                fitment.getVerifiedAt());
    }

    @PostMapping("/review/contributors/{userId}/trust")
    @PreAuthorize(Authorize.COMMONS_REVIEW)
    public StandingView trust(@PathVariable UUID userId, @RequestBody TrustRequest request) {
        return StandingView.of(
                contributions.setTrusted(CurrentUser.userId(), userId, request.trusted()));
    }

    @PostMapping("/review/contributors/{userId}/ban")
    @PreAuthorize(Authorize.COMMONS_REVIEW)
    public StandingView ban(@PathVariable UUID userId, @RequestBody BanRequest request) {
        return StandingView.of(contributions.setBanned(
                CurrentUser.userId(), userId, request.banned(), request.reason()));
    }

    // --- Wire shapes -------------------------------------------------------------------------------

    public record ContributionRequest(@NotNull Kind kind,
                                      UUID targetId,
                                      Map<String, Object> payload,
                                      String reason) {
    }

    public record ReviewNote(String note) {
    }

    public record VerifiedView(UUID fitmentId, UUID componentId, UUID deviceId, Instant verifiedAt) {
    }

    public record TrustRequest(boolean trusted) {
    }

    public record BanRequest(boolean banned, String reason) {
    }

    public record BrandView(UUID id, String name, String logoUrl) {
        static BrandView of(CatalogBrand brand) {
            return new BrandView(brand.getId(), brand.getName(), brand.getLogoUrl());
        }
    }

    public record DeviceView(UUID id,
                             UUID brandId,
                             String name,
                             String variant,
                             String modelCode,
                             Integer releaseYear) {
        static DeviceView of(CatalogDevice device) {
            return new DeviceView(device.getId(), device.getBrandId(), device.getName(),
                    device.getVariant(), device.getModelCode(), device.getReleaseYear());
        }
    }

    public record ComponentView(UUID id,
                                String categoryCode,
                                String name,
                                String description,
                                Map<String, Object> attributes) {
        static ComponentView of(com.fixflow.commons.domain.CatalogEntities.CatalogComponent component) {
            return new ComponentView(component.getId(), component.getCategoryCode(),
                    component.getName(), component.getDescription(), component.getAttributes());
        }
    }

    /**
     * One edge as a client sees it.
     *
     * <p>Carries the counts and the dispute flag rather than a single "compatible" boolean, because
     * whoever is deciding whether to order a part needs to tell "one person said so" from "forty shops
     * fitted it".
     */
    public record FitView(UUID fitmentId,
                          UUID componentId,
                          String componentName,
                          UUID deviceId,
                          String deviceName,
                          String fit,
                          int confirmations,
                          int disputes,
                          boolean disputed,
                          boolean verified) {

        static FitView of(CommonsCatalogService.FitmentView view) {
            return new FitView(
                    view.fitment().getId(),
                    view.fitment().getComponentId(),
                    view.component() == null ? null : view.component().getName(),
                    view.fitment().getDeviceId(),
                    view.device() == null ? null : view.device().getName(),
                    view.fitment().getFitQuality().name(),
                    view.fitment().getConfirmations(),
                    view.fitment().getDisputes(),
                    view.fitment().isDisputed(),
                    view.fitment().isVerified());
        }
    }

    public record ContributionView(UUID id,
                                   Kind kind,
                                   CatalogContribution.Status status,
                                   UUID targetId,
                                   UUID appliedId,
                                   String reason,
                                   String reviewNote,
                                   Instant createdAt,
                                   Instant reviewedAt) {
        static ContributionView of(CatalogContribution contribution) {
            return new ContributionView(contribution.getId(), contribution.getKind(),
                    contribution.getStatus(), contribution.getTargetId(), contribution.getAppliedId(),
                    contribution.getReason(), contribution.getReviewNote(),
                    contribution.getCreatedAt(), contribution.getReviewedAt());
        }
    }

    public record StandingView(int accepted,
                               int rejected,
                               boolean trusted,
                               boolean banned,
                               String bannedReason) {
        static StandingView of(CatalogContributor contributor) {
            return new StandingView(contributor.getAcceptedCount(), contributor.getRejectedCount(),
                    contributor.isTrusted(), contributor.isBanned(), contributor.getBannedReason());
        }
    }
}
