package com.fixflow.bootstrap;

import com.fixflow.catalog.domain.Brand;
import com.fixflow.catalog.domain.Category;
import com.fixflow.catalog.domain.DeviceModel;
import com.fixflow.catalog.domain.Product;
import com.fixflow.catalog.dto.CatalogDtos.CompatibilityGroupRequest;
import com.fixflow.catalog.dto.CatalogDtos.ProductVariantRequest;
import com.fixflow.catalog.repository.BrandRepository;
import com.fixflow.catalog.repository.CategoryRepository;
import com.fixflow.catalog.repository.DeviceModelRepository;
import com.fixflow.catalog.repository.ProductRepository;
import com.fixflow.catalog.repository.ProductVariantRepository;
import com.fixflow.catalog.service.BrandService;
import com.fixflow.catalog.service.CompatibilityGroupService;
import com.fixflow.catalog.service.DeviceService;
import com.fixflow.catalog.service.ProductService;
import com.fixflow.commons.domain.CatalogEntities.CatalogComponent;
import com.fixflow.commons.domain.CatalogEntities.FitQuality;
import com.fixflow.commons.repository.CatalogComponentRepository;
import com.fixflow.commons.repository.CatalogDeviceRepository;
import com.fixflow.commons.service.CommonsCatalogService;
import com.fixflow.commons.service.CommonsReviewerService;
import com.fixflow.group.service.SharingGroupService;
import com.fixflow.config.FixFlowProperties;
import com.fixflow.billing.service.BillingService;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Seeds a working repair shop so the flagship workflow is demonstrable
 * immediately: search "Realme 6" in the shared Fitment Catalog, see what fits,
 * see this shop's stock.
 *
 * <p>Under the {@code dev} profile: if shops already exist, unpaid ones get a complimentary
 * FULL_SHOP so local testing is not blocked by Razorpay. A new demo shop is created only on an
 * empty database. The people it creates have no
 * credentials here: each row is matched by email to a Prabhix Identity account the first time that
 * person signs in, so the owner's address must exist in the local Identity to be usable.
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
    private final BrandService brandService;
    private final BrandRepository brandRepository;
    private final DeviceService deviceService;
    private final DeviceModelRepository deviceModelRepository;
    private final CategoryRepository categoryRepository;
    private final CompatibilityGroupService compatibilityGroupService;
    private final ProductService productService;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final SupplierRepository supplierRepository;
    private final CustomerRepository customerRepository;
    private final PriceRuleRepository priceRuleRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final CommonsCatalogService commonsCatalog;
    private final SharingGroupService sharingGroups;
    private final CatalogDeviceRepository catalogDevices;
    private final CatalogComponentRepository catalogComponents;
    private final CommonsReviewerService commonsReviewers;
    private final BillingService billingService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.getDemo().isSeedEnabled()) {
            return;
        }

        UUID existingOwner = userRepository.findWithRolesByEmail(properties.getDemo().getOwnerEmail())
                .map(User::getId)
                .orElse(null);
        seedCommons(existingOwner);

        if (shopRepository.count() > 0) {
            int granted = 0;
            for (Shop shop : shopRepository.findAll()) {
                if (billingService.paymentRequired(shop.getId())) {
                    billingService.grantPilotEntitlements(shop.getId());
                    granted++;
                }
            }
            if (granted > 0) {
                log.info("Granted complimentary FULL_SHOP to {} existing unpaid shop(s) (dev).", granted);
            }
            log.info("Database already has a shop; skipping demo shop seed.");
            return;
        }

        log.info("Seeding MobiStack demo shop...");
        var provisioned = shopProvisioningService.provision(new ShopProvisioningService.NewShop(
                "Mobile Care Hub",
                "Rohan Deshmukh",
                properties.getDemo().getOwnerEmail(),
                "9876543210",
                "Pune"), true);

        UUID shopId = provisioned.shop().getId();
        User owner = provisioned.owner();
        owner.setSystemAdmin(true);
        userRepository.save(owner);
        seedCommons(owner.getId());
        commonsReviewers.grant(owner.getId(), owner.getId(), "Demo seed: owner reviews the shared catalog");
        seedStaff(shopId, owner.getId());
        seedSecondWorkspace(provisioned.owner());
        seedSuppliersAndCustomers(shopId);
        seedPriceRules(shopId);
        seedShopInventory(shopId, owner.getId());
        log.info("Demo shop ready. Sign in through Identity as {}. Owner also has workspace ABC Mobile Repair.",
                properties.getDemo().getOwnerEmail());
    }

    private void seedStaff(UUID shopId, UUID ownerId) {
        Role manager = role(SystemRole.MANAGER);
        Role technician = role(SystemRole.TECHNICIAN);
        Role staff = role(SystemRole.STAFF);

        createUser(shopId, ownerId, "Priya Deshmukh", "manager@prabhixtechnologies.com", "9876500001", manager);
        createUser(shopId, ownerId, "Rahul Patil", "tech@prabhixtechnologies.com", "9876500002", technician);
        createUser(shopId, ownerId, "Sneha Kulkarni", "staff@prabhixtechnologies.com", "9876500003", staff);
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

    /**
     * The shared Fitment Catalog. Per-shop compatibility groups are private notes, not the
     * product's catalog; those are seeded separately as a single example.
     */
    private void seedCommons(UUID actorId) {
        if (actorId == null) {
            return;
        }
        UUID groupId = sharingGroups.ensureDefault(actorId);
        if (catalogDevices.count() > 0) {
            log.info("Shared catalog already has devices; skipping commons seed.");
            return;
        }
        log.info("Seeding the shared Fitment Catalog...");
        record Model(String brand, String name, String code, int year) {
        }
        List<Model> models = List.of(
                new Model("Realme", "Realme 6", "RMX2001", 2020),
                new Model("Realme", "Realme 7", "RMX2151", 2020),
                new Model("Realme", "Realme Narzo 20", "RMX2193", 2020),
                new Model("Apple", "iPhone 11", "A2221", 2019),
                new Model("Apple", "iPhone 12", "A2403", 2020),
                new Model("Apple", "iPhone 12 Pro", "A2407", 2020),
                new Model("Samsung", "Galaxy S21", "SM-G991B", 2021),
                new Model("Xiaomi", "Redmi Note 10", "M2101K7AI", 2021),
                new Model("Xiaomi", "Redmi Note 10S", null, 2021),
                new Model("Vivo", "Vivo Y20", "V2027", 2020)
        );
        record Kind(String code, String suffix, String description) {
        }
        List<Kind> kinds = List.of(
                new Kind("DISPLAY_FOLDER", "Display Folder",
                        "Complete display assembly: panel, touch and frame, replaced as one unit."),
                new Kind("TEMPERED_GLASS", "Tempered Glass", "Screen protector cut for this model."),
                new Kind("BATTERY", "Battery", "Replacement cell."),
                new Kind("BACK_COVER", "Back Cover", "Rear panel or housing."),
                new Kind("POWER_VOLUME_FLEX", "Power Volume Flex", "Side button flex cable.")
        );
        for (Model model : models) {
            var device = commonsCatalog.addDevice(model.brand(), model.name(), null, model.code(),
                    model.year(), actorId);
            for (Kind kind : kinds) {
                var component = commonsCatalog.addComponent(kind.code(),
                        model.name() + " " + kind.suffix(), kind.description(), Map.of(), actorId);
                commonsCatalog.addFitment(component.getId(), device.getId(), FitQuality.EXACT, actorId, groupId);
            }
        }
        record Cross(String component, String category, String device) {
        }
        for (Cross row : List.of(
                new Cross("Realme 6 Display Folder", "DISPLAY_FOLDER", "Realme 7"),
                new Cross("Realme 6 Display Folder", "DISPLAY_FOLDER", "Realme Narzo 20"),
                new Cross("Realme 6 Tempered Glass", "TEMPERED_GLASS", "Realme 7"),
                new Cross("Realme 6 Tempered Glass", "TEMPERED_GLASS", "Realme Narzo 20"),
                new Cross("iPhone 12 Display Folder", "DISPLAY_FOLDER", "iPhone 12 Pro"),
                new Cross("Redmi Note 10 Display Folder", "DISPLAY_FOLDER", "Redmi Note 10S")
        )) {
            var component = catalogComponents.findByIdentity(row.category(), row.component()).orElse(null);
            var device = catalogDevices.search(row.device(), org.springframework.data.domain.PageRequest.of(0, 1))
                    .getContent().stream()
                    .filter(item -> item.getName().equalsIgnoreCase(row.device()))
                    .findFirst()
                    .orElse(null);
            if (component != null && device != null) {
                commonsCatalog.addFitment(component.getId(), device.getId(), FitQuality.COMPATIBLE, actorId, groupId);
            }
        }
    }

    /**
     * Private shop stock, linked to the shared catalog. One private fitment note is kept so
     * "Propose to shared catalog" has something to show.
     */
    private void seedShopInventory(UUID shopId, UUID ownerId) {
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
        device(shopId, apple, "iPhone 11", "A2221", 2019, List.of("iPhone 11 2019"));

        UUID display = category(shopId, "DISPLAY_FOLDER");
        UUID glass = category(shopId, "TEMPERED_GLASS");
        UUID oca = category(shopId, "TOUCH_OCA");
        UUID battery = category(shopId, "BATTERY");
        UUID back = category(shopId, "BACK_COVER");
        UUID frame = category(shopId, "FRAME");
        UUID flex = category(shopId, "POWER_VOLUME_FLEX");

        compatibilityGroupService.create(shopId, new CompatibilityGroupRequest(
                "BENCH_NOTE_REALME_DISPLAY",
                "Realme 6 family — bench note",
                display,
                "Same 6.5\" IPS panel. Propose this to the shared catalog once confirmed.",
                false, true,
                List.of(realme6.getId(), realme7.getId(), realmeNarzo.getId()), null));

        Supplier a1 = supplierRepository.findByShopIdAndName(shopId, "A1 Mobile Parts").orElseThrow();
        Supplier gz = supplierRepository.findByShopIdAndName(shopId, "Guangzhou Display Hub").orElseThrow();

        addPart(shopId, display, realme.getId(), "Realme 6 / 7 Display",
                catalogPart("DISPLAY_FOLDER", "Realme 6 Display Folder"),
                List.of(variant("GX Incell", "GX", "A+", null, gz.getId(),
                                "2800", "4500", "4000", "4800", "3500", "3200",
                                2, 3, 90, "A1-D-01"),
                        variant("ZY Soft OLED", "ZY", "A", null, gz.getId(),
                                "2200", "3800", "3400", "4000", "3000", "2800",
                                1, 2, 30, "A1-D-01")));

        addPart(shopId, glass, realme.getId(), "Realme 6 / 7 Tempered Glass",
                catalogPart("TEMPERED_GLASS", "Realme 6 Tempered Glass"),
                List.of(variant("2.5D Clear", null, "A", null, a1.getId(),
                        "50", "80", "70", "100", "60", "50",
                        12, 8, 0, "A1-G-02")));

        addPart(shopId, oca, realme.getId(), "Realme 6 / 7 OCA", null,
                List.of(variant("250um sheet", null, "A", null, a1.getId(),
                        "18", "40", "30", "50", "25", "20",
                        8, 10, 0, "A1-G-02")));

        addPart(shopId, battery, realme.getId(), "Realme 6 Battery 4300mAh",
                catalogPart("BATTERY", "Realme 6 Battery"),
                List.of(variant("OEM equivalent", "OEM", "A", null, a1.getId(),
                        "280", "450", "400", "520", "350", "300",
                        3, 4, 90, "A1-B-03")));

        addPart(shopId, back, realme.getId(), "Realme 6 Back Cover",
                catalogPart("BACK_COVER", "Realme 6 Back Cover"),
                List.of(variant("Comet White", null, "A", "White", a1.getId(),
                                "90", "180", "150", "200", "120", "100",
                                3, 3, 0, "A1-C-04"),
                        variant("Lightning Blue", null, "A", "Blue", a1.getId(),
                                "90", "180", "150", "200", "120", "100",
                                2, 3, 0, "A1-C-04")));

        addPart(shopId, frame, realme.getId(), "Realme 6 Middle Frame", null,
                List.of(variant("Aftermarket", null, "B", "Black", a1.getId(),
                        "220", "380", "340", "420", "280", "250",
                        1, 2, 0, "A1-C-04")));

        addPart(shopId, flex, realme.getId(), "Realme 6 Power Volume Flex",
                catalogPart("POWER_VOLUME_FLEX", "Realme 6 Power Volume Flex"),
                List.of(variant("Original pull", null, "A", null, a1.getId(),
                        "40", "90", "75", "110", "60", "50",
                        4, 4, 0, "A1-F-05")));

        addPart(shopId, display, apple.getId(), "iPhone 11 Display",
                catalogPart("DISPLAY_FOLDER", "iPhone 11 Display Folder"),
                List.of(variant("GX Hard OLED", "GX", "A+", "Black", gz.getId(),
                                "2800", "4500", "4000", "4800", "3500", "3200",
                                3, 2, 180, "A1-D-11"),
                        variant("Incell", "Incell", "A", "Black", gz.getId(),
                                "1600", "2800", "2500", "3000", "2200", "2000",
                                2, 2, 90, "A1-D-11")));

        addPart(shopId, glass, apple.getId(), "iPhone 11 Tempered Glass",
                catalogPart("TEMPERED_GLASS", "iPhone 11 Tempered Glass"),
                List.of(variant("9H Clear", null, "A", null, a1.getId(),
                        "50", "150", "120", "180", "90", "70",
                        18, 10, 0, "A1-G-11")));
    }

    private UUID catalogPart(String categoryCode, String name) {
        return catalogComponents.findByIdentity(categoryCode, name).map(CatalogComponent::getId).orElse(null);
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
                         UUID catalogComponentId, List<ProductVariantRequest> variants) {
        Product product = productRepository.save(applyProduct(shopId, categoryId, brandId, name));
        variants.forEach(variant -> {
            var added = productService.addVariant(shopId, product.getId(), variant);
            if (catalogComponentId != null) {
                productVariantRepository.findByIdAndShopId(added.id(), shopId).ifPresent(row -> {
                    row.setCatalogComponentId(catalogComponentId);
                    productVariantRepository.save(row);
                });
            }
        });
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

    private void createUser(UUID shopId, UUID invitedBy, String name, String email, String phone, Role role) {
        User user = new User();
        user.setShopId(shopId);
        user.setFullName(name);
        user.setEmail(email);
        user.setPhone(phone);
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
