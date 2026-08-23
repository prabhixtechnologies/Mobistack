package com.fixflow.party.domain;

import com.fixflow.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "customers")
public class Customer extends AuditableEntity {

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "normalized_name")
    private String normalizedName;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "city", length = 120)
    private String city;

    /** Drives which price a walk-in versus a trade buyer is quoted. */
    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, length = 20)
    private CustomerType customerType = CustomerType.RETAIL;

    @Column(name = "gst_number", length = 20)
    private String gstNumber;

    @Column(name = "credit_limit", nullable = false, precision = 14, scale = 2)
    private BigDecimal creditLimit = BigDecimal.ZERO;

    @Column(name = "outstanding_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal outstandingAmount = BigDecimal.ZERO;

    @Column(name = "total_purchases", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalPurchases = BigDecimal.ZERO;

    @Column(name = "last_transaction_at")
    private Instant lastTransactionAt;

    @Column(name = "notes")
    private String notes;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
