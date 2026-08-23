package com.fixflow.shop.domain;

import com.fixflow.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "shops")
public class Shop extends AuditableEntity {

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", length = 120)
    private String city;

    @Column(name = "state", length = 120)
    private String state;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country", length = 80)
    private String country = "India";

    @Column(name = "gst_number", length = 20)
    private String gstNumber;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "INR";

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "Asia/Kolkata";

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "invoice_prefix", nullable = false, length = 12)
    private String invoicePrefix = "INV";

    @Column(name = "invoice_next_number", nullable = false)
    private long invoiceNextNumber = 1L;

    @Column(name = "repair_prefix", nullable = false, length = 12)
    private String repairPrefix = "JOB";

    @Column(name = "repair_next_number", nullable = false)
    private long repairNextNumber = 1L;

    @Column(name = "require_compatibility_approval", nullable = false)
    private boolean requireCompatibilityApproval;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", nullable = false)
    private Map<String, Object> settings = new HashMap<>();

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /**
     * Human join code such as {@code SHARMA-7K2P}. Scanning / typing it
     * creates a PENDING membership, not automatic access.
     */
    @Column(name = "join_code", nullable = false, length = 16)
    private String joinCode;

    /** Concurrent signed-in devices per user in this workspace. */
    @Column(name = "max_devices_per_user", nullable = false)
    private int maxDevicesPerUser = 3;
}
