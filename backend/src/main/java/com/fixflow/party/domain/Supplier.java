package com.fixflow.party.domain;

import com.fixflow.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "suppliers")
public class Supplier extends AuditableEntity {

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "normalized_name")
    private String normalizedName;

    @Column(name = "contact_person", length = 160)
    private String contactPerson;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "city", length = 120)
    private String city;

    @Column(name = "state", length = 120)
    private String state;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "gst_number", length = 20)
    private String gstNumber;

    @Column(name = "payment_terms_days", nullable = false)
    private int paymentTermsDays;

    @Column(name = "opening_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    /** Maintained by the purchase ledger. */
    @Column(name = "outstanding_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal outstandingAmount = BigDecimal.ZERO;

    @Column(name = "notes")
    private String notes;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
