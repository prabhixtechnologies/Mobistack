package com.fixflow.admin.service;

import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.security.CurrentUser;
import com.fixflow.shop.domain.Shop;
import com.fixflow.shop.repository.ShopRepository;
import com.fixflow.user.repository.UserRepository;
import com.fixflow.workspace.domain.MembershipStatus;
import com.fixflow.workspace.repository.WorkspaceMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatformAdminService {

    public record WorkspaceAdminCard(UUID id, String name, String city, boolean active, long members,
                                    int extraScreens, int screenSeats) {
    }

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final WorkspaceMembershipRepository membershipRepository;

    public void requireAdmin() {
        var user = userRepository.findById(CurrentUser.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication required."));
        if (!user.isSystemAdmin()) {
            throw ApiException.forbidden("Platform admin only.");
        }
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
}
