package com.fixflow.bootstrap;

import com.fixflow.catalog.domain.Brand;
import com.fixflow.catalog.domain.Category;
import com.fixflow.catalog.domain.DeviceModel;
import com.fixflow.catalog.domain.Product;
import com.fixflow.catalog.dto.CatalogDtos.CompatibilityGroupRequest;
import com.fixflow.catalog.dto.CatalogDtos.CompatibilityLinkRequest;
import com.fixflow.catalog.dto.CatalogDtos.ProductVariantRequest;
import com.fixflow.catalog.repository.BrandRepository;
import com.fixflow.catalog.repository.CategoryRepository;
import com.fixflow.catalog.repository.DeviceModelRepository;
import com.fixflow.catalog.repository.ProductRepository;
import com.fixflow.catalog.service.BrandService;
import com.fixflow.catalog.service.CompatibilityGroupService;
import com.fixflow.catalog.service.DeviceService;
import com.fixflow.catalog.service.ProductService;
import com.fixflow.config.FixFlowProperties;
import com.fixflow.party.domain.Customer;
import com.fixflow.party.domain.CustomerType;
import com.fixflow.party.domain.Supplier;
import com.fixflow.party.repository.CustomerRepository;
import com.fixflow.party.repository.SupplierRepository;
import com.fixflow.pricing.domain.PriceRule;
import com.fixflow.pricing.domain.PricingFlag;
import com.fixflow.pricing.repository.PriceRuleRepository;
import com.fixflow.security.SystemRole;
import com.fixflow.shop.domain.Shop;
import com.fixflow.shop.repository.ShopRepository;
import com.fixflow.shop.service.ShopProvisioningService;
import com.fixflow.user.domain.Role;
import com.fixflow.user.domain.User;
import com.fixflow.user.repository.RoleRepository;
import com.fixflow.user.repository.UserRepository;
import com.fixflow.workspace.dto.WorkspaceDtos.CreateWorkspaceRequest;
import com.fixflow.workspace.service.WorkspaceAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Seeds a working repair shop so the flagship workflow is demonstrable
 * immediately: search "Realme 6", see compatible models, see stock and prices.
 *
 * <p>Only runs on an empty database under the {@code dev} profile.
 */
@Slf4j
@Component
@Profile("dev")
@Order(100)
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    private final FixFlowProperties properties;
    private final ShopRepository shopRepository;
    private final ShopProvisioningService shopProvisioningService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final BrandService brandService;
    private final BrandRepository brandRepository;
    private final DeviceService deviceService;
    private final DeviceModelRepository deviceModelRepository;
    private final CategoryRepository categoryRepository;
    private final CompatibilityGroupService compatibilityGroupService;
    private final ProductService productService;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final CustomerRepository customerRepository;
    private final PriceRuleRepository priceRuleRepository;
    private final WorkspaceAccessService workspaceAccessService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.getDemo().isSeedEnabled()) {
            return;
        }
        if (shopRepository.count() > 0) {
            log.info("Database already has a shop; skipping demo seed.");
            return;
        }

        log.info("Seeding MobiStack demo shop...");
        var provisioned = shopProvisioningService.provision(new ShopProvisioningService.NewShop(
                "Mobile Care Hub",
                "Rohan Deshmukh",
                properties.getDemo().getOwnerEmail(),
                properties.getDemo().getOwnerPassword(),
                "9876543210",
                "Pune"), true);

        UUID shopId = provisioned.shop().getId();
        User owner = provisioned.owner();
        owner.setSystemAdmin(true);
        userRepository.save(owner);
        seedStaff(shopId, owner.getId());
        seedSecondWorkspace(provisioned.owner());
        seedSuppliersAndCustomers(shopId);
        seedPriceRules(shopId);
        seedCatalog(shopId);
        log.info("Demo shop ready. Sign in as {} / {}. Owner also has workspace ABC Mobile Repair.",
                properties.getDemo().getOwnerEmail(), properties.getDemo().getOwnerPassword());
    }

    private void seedStaff(UUID shopId, UUID ownerId) {
        Role manager = role(SystemRole.MANAGER);
        Role technician = role(SystemRole.TECHNICIAN);
        Role staff = role(SystemRole.STAFF);

        createUser(shopId, ownerId, "Priya Deshmukh", "manager@prabhixtechnologies.com", "9876500001", "Manager@123", manager);
        createUser(shopId, ownerId, "Rahul Patil", "tech@prabhixtechnologies.com", "9876500002", "Tech@123", technician);
        createUser(shopId, ownerId, "Sneha Kulkarni", "staff@prabhixtechnologies.com", "9876500003", "Staff@123", staff);
    }

    /**
     * Second workspace so the owner can demonstrate the switcher. Last-selected
     * stays on Mobile Care Hub so login still opens the seeded catalog.
     */
    private void seedSecondWorkspace(User owner) {
        UUID firstWorkspace = owner.getShopId();
        workspaceAccessService.create(owner.getId(), new CreateWorkspaceRequest(
                "ABC Mobile Repair",
                "ABC Mobile Repair",
                owner.getFullName(),
                "9876500999",
                owner.getEmail(),
                null,
                "Mumbai"));
        User refreshed = userRepository.findById(owner.getId()).orElseThrow();
        refreshed.setShopId(firstWorkspace);
        userRepository.save(refreshed);
    }

    private void seedSuppliersAndCustomers(UUID shopId) {
        saveSupplier(shopId, "A1 Mobile Parts", "Suresh Mehta", "9822011122", "Pune", "27AABCA1234A1Z5");
        saveSupplier(shopId, "Guangzhou Display Hub", "Wei Chen", "8613800138000", "Guangzhou", null);
        saveSupplier(shopId, "Bombay Spare World", "Farhan Sheikh", "9872112233", "Mumbai", "27AABCB9876B1Z1");

        saveCustomer(shopId, "Rahul", "9873001122", CustomerType.RETAIL);
        saveCustomer(shopId, "Neha Joshi", "9873003344", CustomerType.VIP);
        saveCustomer(shopId, "TechFix Andheri", "9873005566", CustomerType.WHOLESALE);
    }

    /**
     * The default policy every new shop starts with. A cashier picking
     * WHOLESALE, REPAIR or CLEARANCE is quoted the matching field — no
     * hard-coded branching in the POS.
     */
    private void seedPriceRules(UUID shopId) {
        rule(shopId, "Retail sticker", 100, PricingFlag.NORMAL, PriceRule.BaseField.RETAIL_PRICE);
        rule(shopId, "Wholesale sheet", 80, PricingFlag.WHOLESALE, PriceRule.BaseField.WHOLESALE_PRICE);
        rule(shopId, "Repair job price", 80, PricingFlag.REPAIR, PriceRule.BaseField.REPAIR_PRICE);
        rule(shopId, "VIP rate", 70, PricingFlag.VIP, PriceRule.BaseField.WHOLESALE_PRICE);
        rule(shopId, "Clearance sticker", 60, PricingFlag.CLEARANCE, PriceRule.BaseField.CLEARANCE_PRICE);
        rule(shopId, "Old stock clearance", 50, PricingFlag.OLD_STOCK, PriceRule.BaseField.CLEARANCE_PRICE);
    }

    private void seedCatalog(UUID shopId) {
        Brand realme = brandService.findOrCreate(shopId, "Realme");
        Brand apple = brandService.findOrCreate(shopId, "Apple");
        Brand samsung = brandService.findOrCreate(shopId, "Samsung");
        Brand xiaomi = brandService.findOrCreate(shopId, "Xiaomi");
        Brand vivo = brandService.findOrCreate(shopId, "Vivo");

        realme.setColor("#FFC915");
        apple.setColor("#A3A3A3");
        samsung.setColor("#1428A0");
        xiaomi.setColor("#FF6900");
        vivo.setColor("#415FFF");
        brandRepository.saveAll(List.of(realme, apple, samsung, xiaomi, vivo));

        DeviceModel realme6 = device(shopId, realme, "Realme 6", "RMX2001", 2020,
                List.of("Realme 6i", "RMX2002", "Realme 6s"));
        DeviceModel realme7 = device(shopId, realme, "Realme 7", "RMX2151", 2020,
                List.of("Realme 7i", "RMX2103"));
        DeviceModel realmeNarzo = device(shopId, realme, "Realme Narzo 20", "RMX2193", 2020, List.of());
        DeviceModel iphone11 = device(shopId, apple, "iPhone 11", "A2221", 2019,
                List.of("iPhone 11 2019"));
        DeviceModel iphone12 = device(shopId, apple, "iPhone 12", "A2403", 2020, List.of());
        DeviceModel s21 = device(shopId, samsung, "Galaxy S21", "SM-G991B", 2021, List.of("S21"));
        DeviceModel redmiNote10 = device(shopId, xiaomi, "Redmi Note 10", "M2101K7AI", 2021,
                List.of("Redmi Note 10S"));
        DeviceModel vivoY20 = device(shopId, vivo, "Vivo Y20", "V2027", 2020, List.of());

        UUID display = category(shopId, "DISPLAY_FOLDER");
        UUID glass = category(shopId, "TEMPERED_GLASS");
        UUID oca = category(shopId, "TOUCH_OCA");
        UUID battery = category(shopId, "BATTERY");
        UUID back = category(shopId, "BACK_COVER");
        UUID frame = category(shopId, "FRAME");
        UUID flex = category(shopId, "POWER_VOLUME_FLEX");

        UUID realmeDisplayGroup = compatibilityGroupService.create(shopId, new CompatibilityGroupRequest(
                "REALME_DISPLAY_GROUP_001",
                "Realme 6 / 6i / 7 display family",
                display,
                "Same 6.5\" IPS panel and 24-pin connector. Confirmed on the bench.",
                true, true,
                List.of(realme6.getId(), realme7.getId(), realmeNarzo.getId()), null)).id();

        UUID realmeGlassGroup = compatibilityGroupService.create(shopId, new CompatibilityGroupRequest(
                "REALME_GLASS_GROUP_001",
                "Realme 6 family glass",
                glass, null, true, true,
                List.of(realme6.getId(), realme7.getId(), realmeNarzo.getId()), null)).id();

        UUID realmeBatteryGroup = compatibilityGroupService.create(shopId, new CompatibilityGroupRequest(
                "REALME_BATTERY_GROUP_001",
                "Realme 6 / 7 4300 mAh battery",
                battery, "Narzo 20 takes a different cell.", true, true,
                List.of(realme6.getId(), realme7.getId()), null)).id();

        UUID realmeBackGroup = compatibilityGroupService.create(shopId, new CompatibilityGroupRequest(
                "REALME_BACK_GROUP_001",
                "Realme 6 back cover",
                back, "Realme 7 has a different camera island — do not mix.", true, true,
                List.of(realme6.getId()), null)).id();

        UUID iphone11DisplayGroup = compatibilityGroupService.create(shopId, new CompatibilityGroupRequest(
                "IPHONE_11_DISPLAY_GROUP",
                "iPhone 11 display",
                display, null, true, true,
                List.of(iphone11.getId()), null)).id();

        UUID iphone11GlassGroup = compatibilityGroupService.create(shopId, new CompatibilityGroupRequest(
                "IPHONE_11_GLASS_GROUP",
                "iPhone 11 glass",
                glass, null, true, true,
                List.of(iphone11.getId()), null)).id();

        // Unused in links but present so the catalog is not a single-brand shop.
        compatibilityGroupService.create(shopId, new CompatibilityGroupRequest(
                "S21_DISPLAY_GROUP", "Galaxy S21 display", display, null, true, true,
                List.of(s21.getId()), null));
        compatibilityGroupService.create(shopId, new CompatibilityGroupRequest(
                "NOTE10_DISPLAY_GROUP", "Redmi Note 10 display", display, null, true, true,
                List.of(redmiNote10.getId()), null));
        compatibilityGroupService.create(shopId, new CompatibilityGroupRequest(
                "Y20_DISPLAY_GROUP", "Vivo Y20 display", display, null, true, true,
                List.of(vivoY20.getId()), null));

        Supplier a1 = supplierRepository.findByShopIdAndName(shopId, "A1 Mobile Parts").orElseThrow();
        Supplier gz = supplierRepository.findByShopIdAndName(shopId, "Guangzhou Display Hub").orElseThrow();

        // ---- Realme family parts ------------------------------------
        addPart(shopId, display, realme.getId(), "Realme 6 / 7 Display", realmeDisplayGroup, null,
                List.of(variant("GX Incell", "GX", "A+", null, gz.getId(),
                                "2800", "4500", "4000", "4800", "3500", "3200",
                                2, 3, 90, "A1-D-01"),
                        variant("ZY Soft OLED", "ZY", "A", null, gz.getId(),
                                "2200", "3800", "3400", "4000", "3000", "2800",
                                1, 2, 30, "A1-D-01")));

        addPart(shopId, glass, realme.getId(), "Realme 6 / 7 Tempered Glass", realmeGlassGroup, null,
                List.of(variant("2.5D Clear", null, "A", null, a1.getId(),
                        "50", "80", "70", "100", "60", "50",
                        12, 8, 0, "A1-G-02")));

        addPart(shopId, oca, realme.getId(), "Realme 6 / 7 OCA", realmeDisplayGroup, null,
                List.of(variant("250um sheet", null, "A", null, a1.getId(),
                        "18", "40", "30", "50", "25", "20",
                        8, 10, 0, "A1-G-02")));

        addPart(shopId, battery, realme.getId(), "Realme 6 Battery 4300mAh", realmeBatteryGroup, null,
                List.of(variant("OEM equivalent", "OEM", "A", null, a1.getId(),
                        "280", "450", "400", "520", "350", "300",
                        3, 4, 90, "A1-B-03")));

        addPart(shopId, back, realme.getId(), "Realme 6 Back Cover", realmeBackGroup, null,
                List.of(variant("Comet White", null, "A", "White", a1.getId(),
                                "90", "180", "150", "200", "120", "100",
                                3, 3, 0, "A1-C-04"),
                        variant("Lightning Blue", null, "A", "Blue", a1.getId(),
                                "90", "180", "150", "200", "120", "100",
                                2, 3, 0, "A1-C-04")));

        addPart(shopId, frame, realme.getId(), "Realme 6 Middle Frame", realmeDisplayGroup, null,
                List.of(variant("Aftermarket", null, "B", "Black", a1.getId(),
                        "220", "380", "340", "420", "280", "250",
                        1, 2, 0, "A1-C-04")));

        addPart(shopId, flex, realme.getId(), "Realme 6 Power Volume Flex", null, realme6.getId(),
                List.of(variant("Original pull", null, "A", null, a1.getId(),
                        "40", "90", "75", "110", "60", "50",
                        4, 4, 0, "A1-F-05")));

        // ---- iPhone 11 (the repair-job example from the spec) -------
        addPart(shopId, display, apple.getId(), "iPhone 11 Display", iphone11DisplayGroup, null,
                List.of(variant("GX Hard OLED", "GX", "A+", "Black", gz.getId(),
                                "2800", "4500", "4000", "4800", "3500", "3200",
                                3, 2, 180, "A1-D-11"),
                        variant("Incell", "Incell", "A", "Black", gz.getId(),
                                "1600", "2800", "2500", "3000", "2200", "2000",
                                2, 2, 90, "A1-D-11")));

        addPart(shopId, glass, apple.getId(), "iPhone 11 Tempered Glass", iphone11GlassGroup, null,
                List.of(variant("9H Clear", null, "A", null, a1.getId(),
                        "50", "150", "120", "180", "90", "70",
                        18, 10, 0, "A1-G-11")));
    }

    // -----------------------------------------------------------------

    private DeviceModel device(UUID shopId, Brand brand, String name, String code, int year,
                               List<String> aliases) {
        DeviceModel created = deviceService.findOrCreate(shopId, brand, name);
        created.setModelCode(code);
        created.setReleaseYear(year);
        deviceModelRepository.save(created);
        aliases.forEach(alias -> deviceService.saveAlias(shopId, created.getId(), alias,
                com.fixflow.catalog.domain.DeviceAlias.Source.SYSTEM));
        return created;
    }

    private void addPart(UUID shopId, UUID categoryId, UUID brandId, String name,
                         UUID groupId, UUID deviceId, List<ProductVariantRequest> variants) {
        Product product = productRepository.save(applyProduct(shopId, categoryId, brandId, name));
        if (groupId != null) {
            productService.addCompatibility(shopId, product.getId(),
                    new CompatibilityLinkRequest(groupId, null, null, null));
        }
        if (deviceId != null) {
            productService.addCompatibility(shopId, product.getId(),
                    new CompatibilityLinkRequest(null, deviceId, null, null));
        }
        variants.forEach(variant -> productService.addVariant(shopId, product.getId(), variant));
    }

    private Product applyProduct(UUID shopId, UUID categoryId, UUID brandId, String name) {
        Product product = new Product();
        product.setShopId(shopId);
        product.setCategoryId(categoryId);
        product.setBrandId(brandId);
        product.setName(name);
        product.setUnit("PCS");
        product.setTaxRate(BigDecimal.valueOf(18));
        return product;
    }

    private ProductVariantRequest variant(String variantName, String quality, String grade, String color,
                                          UUID supplierId, String cost, String retail, String wholesale,
                                          String repair, String min, String clearance,
                                          int opening, int reorder, int warranty, String location) {
        return new ProductVariantRequest(null, null, variantName, grade, quality, color, supplierId,
                money(cost), money(retail), money(wholesale), money(repair), money(min), money(clearance),
                opening, reorder, null, warranty, null, false, location, true);
    }

    private UUID category(UUID shopId, String code) {
        return categoryRepository.findByShopIdAndCode(shopId, code)
                .map(Category::getId)
                .orElseThrow(() -> new IllegalStateException("Missing category " + code));
    }

    private void rule(UUID shopId, String name, int priority, PricingFlag flag,
                      PriceRule.BaseField field) {
        PriceRule rule = new PriceRule();
        rule.setShopId(shopId);
        rule.setName(name);
        rule.setPriority(priority);
        rule.setScopeType(PriceRule.ScopeType.ALL);
        rule.setPricingFlag(flag);
        rule.setStrategy(PriceRule.Strategy.USE_FIELD);
        rule.setBaseField(field);
        priceRuleRepository.save(rule);
    }

    private void createUser(UUID shopId, UUID invitedBy, String name, String email, String phone, String password,
                            Role role) {
        User user = new User();
        user.setShopId(shopId);
        user.setFullName(name);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRoles(new LinkedHashSet<>(Set.of(role)));
        userRepository.save(user);
        Shop shop = shopRepository.findById(shopId).orElseThrow();
        workspaceAccessService.activate(user, shop, role, invitedBy);
    }

    private Role role(SystemRole systemRole) {
        return roleRepository.findSystemRoleByCode(systemRole.name())
                .orElseThrow(() -> new IllegalStateException("Missing role " + systemRole));
    }

    private void saveSupplier(UUID shopId, String name, String contact, String phone, String city,
                              String gst) {
        Supplier supplier = new Supplier();
        supplier.setShopId(shopId);
        supplier.setName(name);
        supplier.setContactPerson(contact);
        supplier.setPhone(phone);
        supplier.setCity(city);
        supplier.setGstNumber(gst);
        supplierRepository.save(supplier);
    }

    private void saveCustomer(UUID shopId, String name, String phone, CustomerType type) {
        Customer customer = new Customer();
        customer.setShopId(shopId);
        customer.setName(name);
        customer.setPhone(phone);
        customer.setCustomerType(type);
        customerRepository.save(customer);
    }

    private static BigDecimal money(String rupees) {
        return new BigDecimal(rupees);
    }
}
