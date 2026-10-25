package com.fixflow.group.service;

import com.fixflow.billing.domain.BillingPlan;
import com.fixflow.billing.domain.WorkspaceSubscription;
import com.fixflow.billing.repository.BillingPlanRepository;
import com.fixflow.billing.repository.WorkspaceSubscriptionRepository;
import com.fixflow.common.error.ApiException;
import com.fixflow.group.domain.GroupRole;
import com.fixflow.group.domain.SharingGroup;
import com.fixflow.group.domain.SharingGroupMember;
import com.fixflow.group.repository.SharingGroupMemberRepository;
import com.fixflow.group.repository.SharingGroupRepository;
import com.fixflow.security.Permission;
import com.fixflow.security.UserPrincipal;
import com.fixflow.shop.domain.Shop;
import com.fixflow.shop.repository.ShopRepository;
import com.fixflow.user.domain.User;
import com.fixflow.user.repository.UserRepository;
import com.fixflow.workspace.repository.WorkspaceMembershipRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SharingGroupServiceTest {

    private final SharingGroupRepository groups = mock(SharingGroupRepository.class);
    private final SharingGroupMemberRepository members = mock(SharingGroupMemberRepository.class);
    private final ShopRepository shops = mock(ShopRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final WorkspaceSubscriptionRepository subscriptions = mock(WorkspaceSubscriptionRepository.class);
    private final BillingPlanRepository plans = mock(BillingPlanRepository.class);
    private final WorkspaceMembershipRepository memberships = mock(WorkspaceMembershipRepository.class);
    private final SharingGroupService service = new SharingGroupService(
            groups, members, shops, users, subscriptions, plans, memberships);

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminCannotRemoveAnotherAdmin() {
        UUID ownerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID otherAdminId = UUID.randomUUID();
        SharingGroup group = group(ownerId);
        when(groups.visibleTo(eq(adminId), any(), eq(false))).thenReturn(List.of(group));
        when(members.findByGroupIdAndUserId(group.getId(), adminId)).thenReturn(Optional.of(person(group.getId(), adminId, GroupRole.ADMIN)));
        when(members.findByGroupIdAndUserId(group.getId(), otherAdminId)).thenReturn(Optional.of(person(group.getId(), otherAdminId, GroupRole.ADMIN)));
        signIn(adminId);

        assertThatThrownBy(() -> service.removePerson(group.getId(), otherAdminId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("owner");
        verify(members, never()).delete(any());
    }

    @Test
    void ownerCanRemoveAnAdmin() {
        UUID ownerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        SharingGroup group = group(ownerId);
        SharingGroupMember admin = person(group.getId(), adminId, GroupRole.ADMIN);
        when(groups.visibleTo(eq(ownerId), any(), eq(false))).thenReturn(List.of(group));
        when(members.findByGroupIdAndUserId(group.getId(), adminId)).thenReturn(Optional.of(admin));
        signIn(ownerId);

        service.removePerson(group.getId(), adminId);

        verify(members).delete(admin);
    }

    @Test
    void ownerSeesAShopPaymentAndEmail() {
        UUID ownerId = UUID.randomUUID();
        UUID shopId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        SharingGroup group = group(ownerId);
        group.setJoinCode("FITMEN-VH3Y");
        when(groups.visibleTo(eq(ownerId), any(), eq(false))).thenReturn(List.of(group));
        User owner = new User();
        owner.setId(ownerId);
        owner.setFullName("Prabhix Admin");
        owner.setEmail("admin@prabhix.test");
        when(users.findById(ownerId)).thenReturn(Optional.of(owner));
        SharingGroupMember shopRow = new SharingGroupMember();
        shopRow.setGroupId(group.getId());
        shopRow.setWorkspaceId(shopId);
        shopRow.setRole(GroupRole.MEMBER);
        when(members.findByGroupIdOrderByCreatedAtAsc(group.getId())).thenReturn(List.of(shopRow));
        Shop shop = new Shop();
        shop.setId(shopId);
        shop.setName("Bihar Mobile Union");
        shop.setEmail("union@example.com");
        shop.setPhone("9000000000");
        shop.setCity("Patna");
        shop.setState("Bihar");
        when(shops.findById(shopId)).thenReturn(Optional.of(shop));
        WorkspaceSubscription subscription = new WorkspaceSubscription();
        subscription.setStatus(WorkspaceSubscription.ACTIVE);
        subscription.setPeriodEnd(Instant.parse("2026-11-12T00:00:00Z"));
        subscription.setPlanId(planId);
        when(subscriptions.findById(shopId)).thenReturn(Optional.of(subscription));
        BillingPlan plan = new BillingPlan();
        plan.setId(planId);
        plan.setName("Shop");
        when(plans.findById(planId)).thenReturn(Optional.of(plan));
        when(memberships.findActiveOwners(eq(shopId), any())).thenReturn(List.of());
        signIn(ownerId);

        var detail = service.detail(group.getId());

        var card = detail.members().get(1);
        assertThat(card.email()).isEqualTo("union@example.com");
        assertThat(card.phone()).isEqualTo("9000000000");
        assertThat(card.place()).isEqualTo("Patna, Bihar");
        assertThat(card.planName()).isEqualTo("Shop");
        assertThat(card.planStatus()).isEqualTo("ACTIVE");
        assertThat(card.paidUntil()).isEqualTo(Instant.parse("2026-11-12T00:00:00Z"));
        assertThat(detail.members().get(0).email()).isEqualTo("admin@prabhix.test");
    }

    @Test
    void aMemberDoesNotSeeAnotherShopPayment() {
        UUID ownerId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID shopId = UUID.randomUUID();
        SharingGroup group = group(ownerId);
        group.setJoinCode("FITMEN-VH3Y");
        when(groups.visibleTo(eq(callerId), any(), eq(false))).thenReturn(List.of(group));
        when(members.findByGroupIdAndUserId(group.getId(), callerId))
                .thenReturn(Optional.of(person(group.getId(), callerId, GroupRole.MEMBER)));
        User owner = new User();
        owner.setId(ownerId);
        owner.setFullName("Owner");
        owner.setEmail("owner@prabhix.test");
        when(users.findById(ownerId)).thenReturn(Optional.of(owner));
        SharingGroupMember shopRow = new SharingGroupMember();
        shopRow.setGroupId(group.getId());
        shopRow.setWorkspaceId(shopId);
        when(members.findByGroupIdOrderByCreatedAtAsc(group.getId())).thenReturn(List.of(shopRow));
        Shop shop = new Shop();
        shop.setId(shopId);
        shop.setName("Bihar Mobile Union");
        shop.setEmail("union@example.com");
        when(shops.findById(shopId)).thenReturn(Optional.of(shop));
        signIn(callerId);

        var card = service.detail(group.getId()).members().get(1);

        assertThat(card.label()).isEqualTo("Bihar Mobile Union");
        assertThat(card.email()).isNull();
        assertThat(card.paidUntil()).isNull();
    }

    private static SharingGroup group(UUID ownerId) {
        SharingGroup group = new SharingGroup();
        group.setId(UUID.randomUUID());
        group.setName("Circle");
        group.setOwnerUserId(ownerId);
        return group;
    }

    private static SharingGroupMember person(UUID groupId, UUID userId, GroupRole role) {
        SharingGroupMember row = new SharingGroupMember();
        row.setGroupId(groupId);
        row.setUserId(userId);
        row.setRole(role);
        return row;
    }

    private static void signIn(UUID userId) {
        UserPrincipal principal = new UserPrincipal(
                userId, null, "a@b.c", "A", true, Set.<String>of(), Set.<Permission>of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
