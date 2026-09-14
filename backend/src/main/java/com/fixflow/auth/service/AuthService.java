package com.fixflow.auth.service;

import com.fixflow.auth.dto.AuthDtos.AuthenticatedUser;
import com.fixflow.auth.dto.AuthDtos.WorkspaceSession;
import com.fixflow.billing.domain.PlanCatalog;
import com.fixflow.billing.service.BillingService;
import com.fixflow.common.error.ApiException;
import com.fixflow.security.CurrentUser;
import com.fixflow.security.Permission;
import com.fixflow.security.UserPrincipal;
import com.fixflow.shop.domain.Shop;
import com.fixflow.shop.repository.ShopRepository;
import com.fixflow.user.domain.User;
import com.fixflow.user.repository.UserRepository;
import com.fixflow.workspace.dto.WorkspaceDtos.WorkspaceCard;
import com.fixflow.workspace.service.WorkspaceAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Describes the signed-in person in MobiStack terms.
 *
 * <p>Authentication happened before this class is reached: the bearer token was verified against
 * Prabhix Identity and mapped to a local user by the security filter. What is left is authority,
 * which lives here and nowhere else: the selected shop, the role held in it and the plan it is on.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final BillingService billingService;

    @Transactional(readOnly = true)
    public AuthenticatedUser currentUser(UUID userId) {
        User user = userRepository.findWithRolesById(userId)
                .orElseThrow(() -> ApiException.notFound("User", userId));
        UUID selected = CurrentUser.find()
                .map(UserPrincipal::getShopId)
                .orElse(user.getShopId());
        UserPrincipal principal = workspaceAccessService.principalFor(user, selected);
        return toAuthenticatedUser(user, principal);
    }

    /**
     * Makes {@code workspaceId} the person's selected shop and describes them in it. Clients keep
     * the bearer token they already hold; only the user and workspace list change.
     */
    @Transactional
    public WorkspaceSession switchWorkspace(UUID userId, UUID workspaceId) {
        User user = userRepository.findWithRolesById(userId)
                .orElseThrow(() -> ApiException.notFound("User", userId));
        UserPrincipal principal = workspaceAccessService.select(userId, workspaceId);
        List<WorkspaceCard> workspaces = workspaceAccessService.listMine(user.getId(), principal.getShopId())
                .workspaces();
        return new WorkspaceSession(toAuthenticatedUser(user, principal), workspaces);
    }

    private AuthenticatedUser toAuthenticatedUser(User user, UserPrincipal principal) {
        Shop shop = principal.getShopId() == null ? null
                : shopRepository.findById(principal.getShopId()).orElse(null);
        Set<String> permissions = principal.getPermissions().stream()
                .map(Permission::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        UUID workspaceId = shop == null ? null : shop.getId();
        String workspaceName = shop == null ? null : shop.getName();
        boolean admin = user.isSystemAdmin();
        boolean paymentRequired = workspaceId != null && !admin && billingService.paymentRequired(workspaceId);
        boolean catalogOnly = workspaceId != null && !admin && billingService.catalogOnly(workspaceId);
        List<String> features = admin
                ? List.copyOf(PlanCatalog.CODES)
                : List.copyOf(billingService.features(workspaceId));
        var plan = workspaceId == null || admin ? null : billingService.currentPlan(workspaceId);
        var sub = workspaceId == null || admin ? null : billingService.subscription(workspaceId);
        return new AuthenticatedUser(user.getId(), workspaceId, workspaceName, workspaceId, workspaceName,
                user.getFullName(), user.getEmail(), user.getPhone(), user.getAvatarUrl(),
                principal.getRoles(), permissions, admin,
                user.isEmailVerified(), user.isPhoneVerified(), paymentRequired,
                workspaceId != null && billingService.localActivationAvailable(),
                catalogOnly, features,
                plan == null ? null : plan.code(), plan == null ? null : plan.name(),
                sub == null ? null : sub.getPeriodEnd(),
                principal.has(Permission.COMMONS_REVIEW));
    }
}
