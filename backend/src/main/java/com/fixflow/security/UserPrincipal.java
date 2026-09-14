package com.fixflow.security;

import com.fixflow.user.domain.Role;
import com.fixflow.user.domain.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The authenticated caller. {@code shopId} is the <em>selected workspace</em>
 * (same column as the original shop tenant). It is filled only after membership
 * is verified; it is never taken from a request body.
 */
@Getter
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final UUID shopId;
    private final String email;
    private final String fullName;
    private final boolean enabled;
    private final Set<String> roles;
    private final Set<Permission> permissions;
    private final Collection<GrantedAuthority> authorities;

    public UserPrincipal(UUID id, UUID shopId, String email, String fullName, boolean enabled,
                         Set<String> roles, Set<Permission> permissions) {
        this.id = id;
        this.shopId = shopId;
        this.email = email;
        this.fullName = fullName;
        this.enabled = enabled;
        this.roles = Set.copyOf(roles);
        this.permissions = Set.copyOf(permissions);

        Set<GrantedAuthority> granted = new LinkedHashSet<>();
        permissions.forEach(p -> granted.add(new SimpleGrantedAuthority(p.name())));
        roles.forEach(r -> granted.add(new SimpleGrantedAuthority("ROLE_" + r)));
        this.authorities = Set.copyOf(granted);
    }

    public static UserPrincipal from(User user) {
        return new UserPrincipal(user.getId(), user.getShopId(), user.getEmail(), user.getFullName(),
                user.isActive(), user.roleCodes(), user.effectivePermissions());
    }

    /** Token for a verified ACTIVE membership. Permissions come from that role only. */
    public static UserPrincipal forWorkspace(User user, UUID workspaceId, Role role) {
        return new UserPrincipal(user.getId(), workspaceId, user.getEmail(), user.getFullName(),
                user.isActive(), Set.of(role.getCode()), role.permissionCodes());
    }

    /** Logged in but no workspace selected yet — My Workspaces screen. */
    public static UserPrincipal unscoped(User user) {
        return new UserPrincipal(user.getId(), null, user.getEmail(), user.getFullName(),
                user.isActive(), Set.of(), Set.of());
    }

    /**
     * Adds authorities that are not part of the shop role — today, shared-catalog review.
     */
    public UserPrincipal withPermissions(Set<Permission> extra) {
        if (extra == null || extra.isEmpty()) {
            return this;
        }
        Set<Permission> merged = new LinkedHashSet<>(permissions);
        merged.addAll(extra);
        return new UserPrincipal(id, shopId, email, fullName, enabled, roles, merged);
    }

    /**
     * A oneOps BFF call acting for a staff member who may have no MobiStack shop at all.
     *
     * <p>The id is Identity's, which this database also uses as {@code users.id} when the person
     * has been mirrored — and is still a stable actor id when they have not.
     */
    public static UserPrincipal platformBff(UUID actingUserId) {
        return new UserPrincipal(actingUserId, null,
                "staff+" + actingUserId + "@prabhix.internal", "Platform staff", true,
                Set.of("PLATFORM_BFF"), Set.of());
    }

    public boolean hasWorkspace() {
        return shopId != null;
    }

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /** Never exposed: Identity authenticates, so no password exists on this side at all. */
    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return enabled;
    }

    @Override
    public boolean isAccountNonLocked() {
        return enabled;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return enabled;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
