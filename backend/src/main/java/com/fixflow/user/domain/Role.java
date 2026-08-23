package com.fixflow.user.domain;

import com.fixflow.security.Permission;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * A named bundle of permissions. {@code shopId == null} marks one of the five
 * built-in roles shared by every tenant; a non-null value is a custom role
 * belonging to that shop only.
 */
@Getter
@Setter
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "shop_id")
    private UUID shopId;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "system_role", nullable = false)
    private boolean systemRole;

    @Column(name = "seniority", nullable = false)
    private int seniority = 100;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<PermissionEntity> permissions = new LinkedHashSet<>();

    public Set<Permission> permissionCodes() {
        Set<Permission> codes = EnumSet.noneOf(Permission.class);
        permissions.forEach(p -> codes.add(p.getCode()));
        return codes;
    }
}
