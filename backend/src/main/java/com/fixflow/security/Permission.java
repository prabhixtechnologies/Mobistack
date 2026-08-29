package com.fixflow.security;

/**
 * Capability codes. These mirror the rows seeded into the {@code permissions}
 * table in migration V1 and are granted to the principal as Spring Security
 * authorities (no {@code ROLE_} prefix).
 */
public enum Permission {

    INVENTORY_READ,
    INVENTORY_WRITE,
    INVENTORY_ADJUST,
    CATALOG_READ,
    CATALOG_WRITE,
    SALES_READ,
    SALES_WRITE,
    SALES_VOID,
    PURCHASE_READ,
    PURCHASE_WRITE,
    REPAIR_READ,
    REPAIR_WRITE,
    CUSTOMER_READ,
    CUSTOMER_WRITE,
    SUPPLIER_READ,
    SUPPLIER_WRITE,
    PRICING_READ,
    PRICING_WRITE,
    REPORT_READ,
    USER_READ,
    USER_WRITE,
    USER_INVITE,
    SETTINGS_READ,
    SETTINGS_WRITE,
    AUDIT_READ,
    WORKSPACE_BILLING,
    REPORT_EXPORT,
    COMPATIBILITY_APPROVE,
    COMMONS_REVIEW
}
