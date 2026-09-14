package com.fixflow.admin.service;

import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.shop.domain.Shop;
import com.fixflow.shop.repository.ShopRepository;
import com.fixflow.workspace.domain.MembershipStatus;
import com.fixflow.workspace.repository.WorkspaceMembershipRepository;
import com.prabhix.identity.client.ServiceTokenGuard;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Platform administration of shops, reached only by the oneOps BFF.
 *
 * <p>A shop JWT, even one whose owner was once flagged {@code system_admin}, is not enough.
 * Identity of the caller is the shared service token; identity of the person is
 * {@code X-Prabhix-Acting-User}. That split is what lets a support hire act here without also
 * being a MobiStack shopkeeper.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformAdminService {

    public record WorkspaceAdminCard(UUID id, String name, String city, boolean active, long members,
                                    int extraScreens, int screenSeats) {
    }

    private final ShopRepository shopRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final ServiceTokenGuard serviceToken;

    /**
     * @return the staff member the BFF named, so writes can record a person rather than "the platform"
     */
    public UUID requireAdmin() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication required.");
        }
        if (!serviceToken.configured() || !serviceToken.permits(request)) {
            throw ApiException.forbidden("Platform admin is only reachable from the operations console.");
        }
        UUID actor = serviceToken.actingUser(request).orElseThrow(() ->
                new ApiException(ErrorCode.UNAUTHENTICATED,
                        "X-Prabhix-Acting-User must carry the id of the staff member making this request"));
        if (isMutation(request.getMethod())) {
            log.warn("Platform admin write {} {} actingUser={} reason={}",
                    request.getMethod(), request.getRequestURI(), actor,
                    serviceToken.actingReason(request).orElse("-"));
        }
        return actor;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceAdminCard> listWorkspaces() {
        List<Shop> shops = shopRepository.findAll();
        if (shops.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = shops.stream().map(Shop::getId).toList();
        Map<UUID, Long> memberCounts = new HashMap<>();
        for (var row : membershipRepository.countByWorkspaceIdInAndStatus(ids, MembershipStatus.ACTIVE)) {
            memberCounts.put(row.getWorkspaceId(), row.getTotal());
        }
        return shops.stream()
                .map(shop -> new WorkspaceAdminCard(shop.getId(), shop.getName(), shop.getCity(), shop.isActive(),
                        memberCounts.getOrDefault(shop.getId(), 0L),
                        shop.getExtraScreens(), shop.screenSeats()))
                .toList();
    }

    @Transactional
    public Shop setActive(UUID workspaceId, boolean active) {
        Shop shop = shopRepository.findById(workspaceId)
                .orElseThrow(() -> ApiException.notFound("Workspace", workspaceId));
        shop.setActive(active);
        return shopRepository.save(shop);
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private static boolean isMutation(String method) {
        return method != null && !"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)
                && !"OPTIONS".equalsIgnoreCase(method);
    }
}
