package com.fixflow.user.domain;

import com.fixflow.common.domain.AuditableEntity;
import com.fixflow.security.Permission;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * MobiStack's mirror of a person. The primary key is the Prabhix Identity subject for anyone who
 * has signed in; rows created ahead of that (an invitation by email) get a local id and are matched
 * by email on first sign-in. There is no credential here: passwords, OTPs and sessions are
 * Identity's.
 */
@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends AuditableEntity {

    /** Last-selected workspace. Not the tenant key — memberships are. */
    @Column(name = "shop_id")
    private UUID shopId;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(name = "email", nullable = false, columnDefinition = "citext")
    private String email;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified;

    @Column(name = "system_admin", nullable = false)
    private boolean systemAdmin;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();

    public Set<Permission> effectivePermissions() {
        Set<Permission> all = EnumSet.noneOf(Permission.class);
        roles.forEach(role -> all.addAll(role.permissionCodes()));
        return all;
    }

    public Set<String> roleCodes() {
        Set<String> codes = new LinkedHashSet<>();
        roles.forEach(role -> codes.add(role.getCode()));
        return codes;
    }
}
