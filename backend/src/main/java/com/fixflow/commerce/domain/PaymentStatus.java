package com.fixflow.commerce.domain;

public enum PaymentStatus {
    CREATED,
    PENDING,
    AUTHORIZED,
    CAPTURED,
    FAILED,
    REFUNDED,
    PARTIALLY_REFUNDED,
    EXPIRED
}
