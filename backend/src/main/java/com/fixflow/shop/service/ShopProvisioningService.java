package com.fixflow.shop.service;

import com.fixflow.audit.service.AuditAction;
import com.fixflow.audit.service.AuditService;
import com.fixflow.billing.service.BillingService;
import com.fixflow.catalog.domain.Category;
import com.fixflow.catalog.domain.DefaultCategories;
import com.fixflow.catalog.repository.CategoryRepository;
import com.fixflow.common.error.ApiException;
import com.fixflow.security.SystemRole;
import com.fixflow.shop.domain.Shop;
import com.fixflow.shop.repository.ShopRepository;
import com.fixflow.user.domain.Role;
import com.fixflow.user.domain.User;
import com.fixflow.user.repository.RoleRepository;
import com.fixflow.user.repository.UserRepository;
import com.fixflow.workspace.domain.MembershipStatus;
import com.fixflow.workspace.domain.WorkspaceMembership;
import com.fixflow.workspace.repository.WorkspaceMembershipRepository;
import com.fixflow.workspace.service.JoinCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Creates a shop together with everything it needs to be usable on day one:
 * the default part categories and an OWNER account. Kept separate from
 * {@code AuthService} because the demo seeder and any future admin console
 * need the same behaviour.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopProvisioningService {

    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final WorkspaceMembershipRepository membershipRepository;
    private final BillingService billingService;

    public record NewShop(String shopName, String ownerName, String email, String rawPassword,
                          String phone, String city) {
    }

    public record ProvisionedShop(Shop shop, User owner) {
    }

    @Transactional
    public ProvisionedShop provision(NewShop request) {
        if (userRepository.existsByEmail(request.email())) {
            throw ApiException.alreadyExists("An account with this email already exists.");
        }

        Shop shop = new Shop();
        shop.setName(request.shopName());
        shop.setPhone(request.phone());
        shop.setEmail(request.email());
        shop.setCity(request.city());
        shop.setInvoicePrefix(defaultInvoicePrefix(request.shopName()));
        shop.setJoinCode(allocateJoinCode(request.shopName()));
        shopRepository.save(shop);

        seedCategories(shop);

        Role ownerRole = roleRepository.findSystemRoleByCode(SystemRole.OWNER.name())
                .orElseThrow(() -> new IllegalStateException(
                        "System role OWNER is missing; check migration V1"));

        User owner = new User();
        owner.setShopId(shop.getId());
        owner.setFullName(request.ownerName());
        owner.setEmail(request.email().toLowerCase());
        owner.setPhone(request.phone());
        owner.setPasswordHash(passwordEncoder.encode(request.rawPassword()));
        owner.setRoles(new LinkedHashSet<>(Set.of(ownerRole)));
        userRepository.save(owner);
        attachOwnerMembership(owner, shop);
        billingService.grantPilotEntitlements(shop.getId());

        auditService.recordForShop(shop.getId(), request.ownerName(), AuditAction.SHOP_CREATED,
                "Shop", shop.getId(), "Shop \"%s\" created".formatted(shop.getName()));

        log.info("Provisioned shop {} ({}) with owner {}", shop.getName(), shop.getId(), owner.getEmail());
        return new ProvisionedShop(shop, owner);
    }

    /** Copies the category template so the shop owns editable rows, not shared ones. */
    @Transactional
    public void seedCategories(Shop shop) {
        if (categoryRepository.countByShopId(shop.getId()) > 0) {
            return;
        }
        DefaultCategories.ALL.forEach(template -> {
            Category category = new Category();
            category.setShopId(shop.getId());
            category.setCode(template.code());
            category.setName(template.name());
            category.setIcon(template.icon());
            category.setColor(template.color());
            category.setSortOrder(template.sortOrder());
            category.setCompatibilityRelevant(template.compatibilityRelevant());
            categoryRepository.save(category);
        });
    }

    private void attachOwnerMembership(User owner, Shop shop) {
        Role ownerRole = roleRepository.findSystemRoleByCode(SystemRole.OWNER.name())
                .orElseThrow(() -> new IllegalStateException("System role OWNER is missing"));
        WorkspaceMembership membership = new WorkspaceMembership();
        membership.setWorkspaceId(shop.getId());
        membership.setUserId(owner.getId());
        membership.setRole(ownerRole);
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setJoinedAt(java.time.Instant.now());
        membership.setLastSelectedAt(java.time.Instant.now());
        membershipRepository.save(membership);
    }

    private String allocateJoinCode(String shopName) {
        for (int attempt = 0; attempt < 12; attempt++) {
            String code = JoinCodeGenerator.generate(shopName);
            if (!shopRepository.existsByJoinCode(code)) {
                return code;
            }
        }
        throw ApiException.conflict("Could not allocate a workspace join code.");
    }

    /** "Mobile Care Center" -> "MCC", falling back to INV. */
    private static String defaultInvoicePrefix(String shopName) {
        String initials = java.util.Arrays.stream(shopName.trim().split("\\s+"))
                .filter(word -> !word.isBlank())
                .limit(3)
                .map(word -> String.valueOf(Character.toUpperCase(word.charAt(0))))
                .reduce("", String::concat);
        return initials.isBlank() ? "INV" : initials;
    }
}
