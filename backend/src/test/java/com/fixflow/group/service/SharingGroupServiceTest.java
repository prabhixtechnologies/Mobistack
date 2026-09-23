package com.fixflow.group.service;

import com.fixflow.common.error.ApiException;
import com.fixflow.group.domain.GroupRole;
import com.fixflow.group.domain.SharingGroup;
import com.fixflow.group.domain.SharingGroupMember;
import com.fixflow.group.repository.SharingGroupMemberRepository;
import com.fixflow.group.repository.SharingGroupRepository;
import com.fixflow.security.Permission;
import com.fixflow.security.UserPrincipal;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SharingGroupServiceTest {

    private final SharingGroupRepository groups = mock(SharingGroupRepository.class);
    private final SharingGroupMemberRepository members = mock(SharingGroupMemberRepository.class);
    private final SharingGroupService service = new SharingGroupService(
            groups, members, mock(ShopRepository.class), mock(UserRepository.class));

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
