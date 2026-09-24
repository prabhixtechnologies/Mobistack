package com.fixflow.billing.domain;

import com.fixflow.commerce.domain.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "billing_orders")
public class BillingOrder {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "shop_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "price_code", nullable = false, length = 40)
    private String priceCode;

    @Column(nullable = false, length = 80)
    private String purpose;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PaymentStatus status = PaymentStatus.CREATED;

    @Column(nullable = false, length = 40)
    private String gateway = "DEV";

    @Column(name = "gateway_order_id", length = 80)
    private String gatewayOrderId;

    @Column(name = "gateway_payment_id", length = 80)
    private String gatewayPaymentId;

    @Column(name = "entitlement_code", nullable = false, length = 40)
    private String entitlementCode;

    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
