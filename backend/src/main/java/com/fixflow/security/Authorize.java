package com.fixflow.security;

/**
 * SpEL fragments for {@code @PreAuthorize}. Referencing constants instead of
 * inline strings means a renamed permission breaks the build rather than
 * silently opening an endpoint.
 */
public final class Authorize {

    public static final String INVENTORY_READ = "hasAuthority('INVENTORY_READ')";
    public static final String INVENTORY_WRITE = "hasAuthority('INVENTORY_WRITE')";
    public static final String INVENTORY_ADJUST = "hasAuthority('INVENTORY_ADJUST')";
    public static final String CATALOG_READ = "hasAuthority('CATALOG_READ')";
    public static final String CATALOG_WRITE = "hasAuthority('CATALOG_WRITE')";
    public static final String SALES_READ = "hasAuthority('SALES_READ')";
    public static final String SALES_WRITE = "hasAuthority('SALES_WRITE')";
    public static final String PURCHASE_READ = "hasAuthority('PURCHASE_READ')";
    public static final String PURCHASE_WRITE = "hasAuthority('PURCHASE_WRITE')";
    public static final String REPAIR_READ = "hasAuthority('REPAIR_READ')";
    public static final String REPAIR_WRITE = "hasAuthority('REPAIR_WRITE')";
    public static final String CUSTOMER_READ = "hasAuthority('CUSTOMER_READ')";
    public static final String CUSTOMER_WRITE = "hasAuthority('CUSTOMER_WRITE')";
    public static final String SUPPLIER_READ = "hasAuthority('SUPPLIER_READ')";
    public static final String SUPPLIER_WRITE = "hasAuthority('SUPPLIER_WRITE')";
    public static final String PRICING_READ = "hasAuthority('PRICING_READ')";
    public static final String PRICING_WRITE = "hasAuthority('PRICING_WRITE')";
    public static final String REPORT_READ = "hasAuthority('REPORT_READ')";
    public static final String USER_READ = "hasAuthority('USER_READ')";
    public static final String USER_WRITE = "hasAuthority('USER_WRITE')";
    public static final String USER_INVITE = "hasAuthority('USER_INVITE')";
    public static final String SETTINGS_READ = "hasAuthority('SETTINGS_READ')";
    public static final String SETTINGS_WRITE = "hasAuthority('SETTINGS_WRITE')";
    public static final String AUDIT_READ = "hasAuthority('AUDIT_READ')";
    public static final String WORKSPACE_BILLING = "hasAuthority('WORKSPACE_BILLING')";
    public static final String REPORT_EXPORT = "hasAuthority('REPORT_EXPORT')";
    public static final String COMPATIBILITY_APPROVE = "hasAuthority('COMPATIBILITY_APPROVE')";
    public static final String SALES_VOID = "hasAuthority('SALES_VOID')";

    private Authorize() {
    }
}
