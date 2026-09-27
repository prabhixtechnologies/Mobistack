package com.fixflow.group.service;

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
import com.fixflow.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GroupJoinAdmissionTest {

    private final SharingGroupRepository groups = mock(SharingGroupRepository.class);
    private final SharingGroupMemberRepository members = mock(SharingGroupMemberRepository.class);
    private final ShopRepository shops = mock(ShopRepository.class);
    private final SharingGroupService service = new SharingGroupService(groups, members, shops, mock(UserRepository.class));

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void addShopRequiresJoinCodeNotBareWorkspaceId() {
        UUID ownerId = UUID.randomUUID();
        SharingGroup group = new SharingGroup();
        group.setId(UUID.randomUUID());
        group.setOwnerUserId(ownerId);
        when(groups.visibleTo(eq(ownerId), any(), eq(false))).thenReturn(List.of(group));
        signIn(ownerId);

        assertThatThrownBy(() -> service.addShop(group.getId(), "   "))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("join code");
    }

    @Test
    void admitApprovedShopUsesWorkspaceIdAfterPayment() {
        UUID ownerId = UUID.randomUUID();
        UUID shopId = UUID.randomUUID();
        SharingGroup group = new SharingGroup();
        group.setId(UUID.randomUUID());
        group.setOwnerUserId(ownerId);
        Shop shop = new Shop();
        shop.setId(shopId);
        shop.setName("Counter");
        when(groups.visibleTo(eq(ownerId), any(), eq(false))).thenReturn(List.of(group));
        when(shops.findById(shopId)).thenReturn(Optional.of(shop));
        when(members.findByGroupIdAndWorkspaceId(group.getId(), shopId)).thenReturn(Optional.empty());
        signIn(ownerId);

        service.admitApprovedShop(group.getId(), shopId);
    }

    private static void signIn(UUID userId) {
        UserPrincipal principal = new UserPrincipal(
                userId, null, "a@b.c", "Owner", true, Set.of(), Set.of(Permission.CATALOG_READ));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
