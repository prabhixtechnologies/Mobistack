package com.fixflow.admin.web;

import com.fixflow.admin.service.PlatformAdminService;
import com.fixflow.admin.service.PlatformAdminService.WorkspaceAdminCard;
import com.fixflow.auth.service.DeviceSessionService;
import com.fixflow.billing.domain.BillingOrder;
import com.fixflow.billing.repository.BillingOrderRepository;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    private final DeviceSessionService deviceSessionService;
    private final SupportService supportService;
    private final AppReleaseService appReleaseService;
    private final ShopRepository shopRepository;

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
    public List<BillingOrder> orders() {
        platformAdminService.requireAdmin();
        return billingOrderRepository.findAll(PageRequest.of(0, 80)).getContent();
    }

    @GetMapping("/live")
    public List<PresenceSnapshot> live() {
        platformAdminService.requireAdmin();
        return presenceService.liveAll();
    }

    @GetMapping("/sessions/{userId}")
    public List<DeviceSessionService.SessionCard> sessions(@PathVariable UUID userId) {
        platformAdminService.requireAdmin();
        return deviceSessionService.listMine(userId, null);
    }

    @PostMapping("/sessions/{userId}/revoke-device")
    public Map<String, Integer> revokeDevice(@PathVariable UUID userId, @RequestBody Map<String, String> body) {
        platformAdminService.requireAdmin();
        int revoked = deviceSessionService.revokeUserDevice(userId, body.get("deviceId"));
        return Map.of("revoked", revoked);
    }

    @PostMapping("/sessions/{userId}/revoke-all")
    public Map<String, Integer> revokeAll(@PathVariable UUID userId) {
        platformAdminService.requireAdmin();
        return Map.of("revoked", deviceSessionService.revokeAll(userId));
    }

    @PostMapping("/workspaces/{id}/device-limit")
    public Shop deviceLimit(@PathVariable UUID id, @RequestBody Map<String, Integer> body) {
        platformAdminService.requireAdmin();
        Shop shop = shopRepository.findById(id).orElseThrow();
        int cap = Math.max(1, Math.min(20, body.getOrDefault("maxDevicesPerUser", 3)));
        shop.setMaxDevicesPerUser(cap);
        return shopRepository.save(shop);
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
}
