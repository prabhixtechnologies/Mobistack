package com.fixflow.workspace.service;

import com.fixflow.audit.service.AuditService;
import com.fixflow.billing.service.BillingService;
import com.fixflow.catalog.repository.ProductRepository;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.security.UserPrincipal;
import com.fixflow.shop.domain.Shop;
import com.fixflow.shop.repository.ShopRepository;
import com.fixflow.shop.service.ShopProvisioningService;
import com.fixflow.user.domain.Role;
import com.fixflow.user.domain.User;
import com.fixflow.user.repository.RoleRepository;
import com.fixflow.user.repository.UserRepository;
import com.fixflow.workspace.domain.MembershipStatus;
import com.fixflow.workspace.domain.WorkspaceMembership;
import com.fixflow.workspace.dto.WorkspaceDtos.WorkspaceCard;
import com.fixflow.workspace.repository.WorkspaceMembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceAccessServiceTest {

    @Mock
    private WorkspaceMembershipRepository membershipRepository;
    @Mock
    private ShopRepository shopRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ShopProvisioningService shopProvisioningService;
    @Mock
    private AuditService auditService;
    @Mock
    private BillingService billingService;

    @InjectMocks
    private WorkspaceAccessService service;

    private UUID userId;
    private UUID workspaceA;
    private UUID workspaceB;
    private User user;
    private Shop shopA;
    private Role ownerRole;
    private Role staffRole;
    private Role viewerRole;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        workspaceA = UUID.randomUUID();
        workspaceB = UUID.randomUUID();

        ownerRole = role("OWNER", 0);
        staffRole = role("STAFF", 40);
        viewerRole = role("VIEWER", 90);

        user = new User();
        user.setId(userId);
        user.setFullName("Abhishek Sharma");
        user.setEmail("owner@fixflow.app");
        user.setActive(true);
        user.setRoles(Set.of(ownerRole));
        user.setShopId(workspaceA);

        shopA = shop(workspaceA, "Mobile Care Hub");
    }

    @Test
    void requireActiveRejectsAWorkspaceTheUserDoesNotBelongTo() {
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceB, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireActive(userId, workspaceB))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.NOT_A_MEMBER);
    }

    @Test
    void pendingMembershipCannotSelectOrCallBusinessApis() {
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceA, userId))
                .thenReturn(Optional.of(membership(workspaceA, MembershipStatus.PENDING, viewerRole)));

        assertThatThrownBy(() -> service.select(userId, workspaceA))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.MEMBERSHIP_INACTIVE);
    }

    @Test
    void suspendedMembershipCannotSelect() {
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceA, userId))
                .thenReturn(Optional.of(membership(workspaceA, MembershipStatus.SUSPENDED, staffRole)));

        assertThatThrownBy(() -> service.select(userId, workspaceA))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.MEMBERSHIP_INACTIVE);
    }

    @Test
    void deactivatedWorkspaceIsRejectedEvenWithAnActiveMembership() {
        shopA.setActive(false);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceA, userId))
                .thenReturn(Optional.of(membership(workspaceA, MembershipStatus.ACTIVE, ownerRole)));
        when(shopRepository.findById(workspaceA)).thenReturn(Optional.of(shopA));

        assertThatThrownBy(() -> service.requireActive(userId, workspaceA))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.ACCOUNT_DISABLED);
    }

    @Test
    void joinByCodeStaysPendingAndDoesNotGrantAccess() {
        when(shopRepository.findByJoinCodeIgnoreCase("HUB-7K2P")).thenReturn(Optional.of(shopA));
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceA, userId)).thenReturn(Optional.empty());
        when(roleRepository.findSystemRoleByCode("VIEWER")).thenReturn(Optional.of(viewerRole));
        when(membershipRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(shopRepository.findById(workspaceA)).thenReturn(Optional.of(shopA));
        when(membershipRepository.countByWorkspaceIdAndStatus(workspaceA, MembershipStatus.ACTIVE)).thenReturn(3L);
        when(productRepository.countByShopIdAndActiveTrue(workspaceA)).thenReturn(12L);

        WorkspaceCard card = service.requestJoin(userId, "hub-7k2p");

        assertThat(card.status()).isEqualTo(MembershipStatus.PENDING);
        assertThat(card.selected()).isFalse();
        assertThat(card.role()).isEqualTo("VIEWER");
    }

    @Test
    void joinByCodeDoesNotCreateASecondMembershipWhenAlreadyActive() {
        when(shopRepository.findByJoinCodeIgnoreCase("HUB-7K2P")).thenReturn(Optional.of(shopA));
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceA, userId))
                .thenReturn(Optional.of(membership(workspaceA, MembershipStatus.ACTIVE, ownerRole)));

        assertThatThrownBy(() -> service.requestJoin(userId, "HUB-7K2P"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.ALREADY_EXISTS);
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void removedMemberCanRequestToJoinAgainAsPending() {
        WorkspaceMembership removed = membership(workspaceA, MembershipStatus.REMOVED, viewerRole);
        when(shopRepository.findByJoinCodeIgnoreCase("HUB-7K2P")).thenReturn(Optional.of(shopA));
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceA, userId)).thenReturn(Optional.of(removed));
        when(membershipRepository.save(removed)).thenReturn(removed);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(shopRepository.findById(workspaceA)).thenReturn(Optional.of(shopA));
        when(membershipRepository.countByWorkspaceIdAndStatus(workspaceA, MembershipStatus.ACTIVE)).thenReturn(3L);
        when(productRepository.countByShopIdAndActiveTrue(workspaceA)).thenReturn(0L);

        WorkspaceCard card = service.requestJoin(userId, "HUB-7K2P");

        assertThat(removed.getStatus()).isEqualTo(MembershipStatus.PENDING);
        assertThat(card.status()).isEqualTo(MembershipStatus.PENDING);
        verify(membershipRepository).save(removed);
    }

    @Test
    void principalUsesMembershipRoleNotTheUsersGlobalRoles() {
        when(membershipRepository.findActiveForUser(userId, MembershipStatus.ACTIVE))
                .thenReturn(List.of(membership(workspaceA, MembershipStatus.ACTIVE, staffRole)));

        UserPrincipal principal = service.principalFor(user, workspaceA);

        assertThat(principal.getShopId()).isEqualTo(workspaceA);
        assertThat(principal.getRoles()).containsExactly("STAFF");
        assertThat(principal.getRoles()).doesNotContain("OWNER");
    }

    @Test
    void principalIsUnscopedWhenTheUserHasNoActiveMembership() {
        when(membershipRepository.findActiveForUser(userId, MembershipStatus.ACTIVE)).thenReturn(List.of());

        UserPrincipal principal = service.principalFor(user, workspaceA);

        assertThat(principal.hasWorkspace()).isFalse();
        assertThat(principal.getRoles()).isEmpty();
    }

    private static Role role(String code, int seniority) {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setCode(code);
        role.setName(code);
        role.setSeniority(seniority);
        role.setSystemRole(true);
        return role;
    }

    private static Shop shop(UUID id, String name) {
        Shop shop = new Shop();
        shop.setId(id);
        shop.setName(name);
        shop.setCity("Pune");
        shop.setJoinCode("HUB-7K2P");
        shop.setActive(true);
        return shop;
    }

    private WorkspaceMembership membership(UUID workspaceId, MembershipStatus status, Role role) {
        WorkspaceMembership membership = new WorkspaceMembership();
        membership.setId(UUID.randomUUID());
        membership.setWorkspaceId(workspaceId);
        membership.setUserId(userId);
        membership.setRole(role);
        membership.setStatus(status);
        return membership;
    }
}
