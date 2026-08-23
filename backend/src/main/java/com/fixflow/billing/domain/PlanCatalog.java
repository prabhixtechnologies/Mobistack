package com.fixflow.billing.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Product features an admin can attach to a sellable plan.
 *
 * Codes are stable: the sidebar, route guard, and API entitlements all resolve
 * from the same list so a plan tick box cannot drift from what the shop sees.
 */
public final class PlanCatalog {

    public record Feature(String code, String label, String help) {
    }

    public static final List<Feature> ALL = List.of(
            new Feature("COMPATIBILITY", "Compatibility", "Look up which parts fit a phone"),
            new Feature("DASHBOARD", "Dashboard", "Home numbers and today's work"),
            new Feature("SALES", "Sales", "Take a sale at the counter"),
            new Feature("REPAIRS", "Repairs", "Intake and track repair jobs"),
            new Feature("INVENTORY", "Inventory", "Stock on the shelf"),
            new Feature("PURCHASES", "Purchases", "Buy stock from suppliers"),
            new Feature("CUSTOMERS", "Customers", "Walk-in and trade buyers"),
            new Feature("SUPPLIERS", "Suppliers", "Who you buy parts from"),
            new Feature("MEMBERS", "People", "Members and access control"),
            new Feature("IMPORT", "Import", "Bulk catalog import"),
            new Feature("REPORTS", "Reports", "Sales and stock reports"),
            new Feature("MOVEMENTS", "Movements", "Stock movement history"),
            new Feature("AUDIT", "Audit", "Who changed what")
    );

    public static final Set<String> CODES = ALL.stream()
            .map(Feature::code)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

    private PlanCatalog() {
    }

    public static boolean known(String code) {
        return CODES.contains(code);
    }

    public static List<String> entitlementsFor(Set<String> features) {
        Set<String> codes = new LinkedHashSet<>();
        codes.add("WORKSPACE_CREATE");
        if (containsAny(features, "COMPATIBILITY", "IMPORT", "DASHBOARD")) {
            codes.add("CATALOG");
        }
        if (features.contains("SALES")) {
            codes.add("SALES");
        }
        if (features.contains("REPAIRS")) {
            codes.add("REPAIRS");
        }
        if (containsAny(features, "INVENTORY", "PURCHASES", "MOVEMENTS")) {
            codes.add("INVENTORY");
        }
        if (features.contains("MEMBERS")) {
            codes.add("MEMBER_ADD");
            codes.add("MULTI_USER");
        }
        return List.copyOf(codes);
    }

    public static String routeFeature(String pathname) {
        if (pathname == null || pathname.isBlank()) {
            return null;
        }
        if ("/".equals(pathname)) {
            return "DASHBOARD";
        }
        if (pathname.startsWith("/devices") || pathname.startsWith("/search")
                || pathname.startsWith("/compatibility")) {
            return "COMPATIBILITY";
        }
        if (pathname.startsWith("/sales")) {
            return "SALES";
        }
        if (pathname.startsWith("/repairs")) {
            return "REPAIRS";
        }
        if (pathname.startsWith("/inventory")) {
            return "INVENTORY";
        }
        if (pathname.startsWith("/purchases")) {
            return "PURCHASES";
        }
        if (pathname.startsWith("/customers")) {
            return "CUSTOMERS";
        }
        if (pathname.startsWith("/suppliers")) {
            return "SUPPLIERS";
        }
        if (pathname.startsWith("/members") || pathname.startsWith("/users")) {
            return "MEMBERS";
        }
        if (pathname.startsWith("/import")) {
            return "IMPORT";
        }
        if (pathname.startsWith("/reports")) {
            return "REPORTS";
        }
        if (pathname.startsWith("/movements")) {
            return "MOVEMENTS";
        }
        if (pathname.startsWith("/audit") || pathname.startsWith("/health")) {
            return "AUDIT";
        }
        return null;
    }

    public static String homeFeaturePath(Set<String> features) {
        if (features.contains("DASHBOARD")) {
            return "/";
        }
        if (features.contains("COMPATIBILITY")) {
            return "/compatibility";
        }
        if (features.contains("SALES")) {
            return "/sales";
        }
        if (features.contains("INVENTORY")) {
            return "/inventory";
        }
        return "/billing";
    }

    private static boolean containsAny(Set<String> features, String... codes) {
        for (String code : codes) {
            if (features.contains(code)) {
                return true;
            }
        }
        return false;
    }
}
