package com.fixflow.commerce.service;

import com.fixflow.commerce.dto.CommerceDtos.PaymentRequest;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class CommerceValidation {

    public static final int MONEY_SCALE = 2;
    public static final BigDecimal MAX_LINE_DISCOUNT_RATIO = new BigDecimal("0.50");
    public static final BigDecimal MAX_HEADER_DISCOUNT_RATIO = new BigDecimal("0.50");
    private static final BigDecimal PRICE_EPSILON = new BigDecimal("0.01");

    private CommerceValidation() {
    }

    public static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static void requirePositiveQuantity(int quantity) {
        if (quantity <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Quantity must be positive.");
        }
    }

    public static void requireNonNegative(BigDecimal amount, String field) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, field + " cannot be negative.");
        }
    }

    public static void requireLineDiscountCap(BigDecimal unitPrice, int quantity, BigDecimal discount) {
        BigDecimal lineGross = money(unitPrice).multiply(BigDecimal.valueOf(quantity));
        BigDecimal cap = lineGross.multiply(MAX_LINE_DISCOUNT_RATIO);
        if (money(discount).compareTo(cap) > 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Line discount cannot exceed 50% of the line total.");
        }
    }

    public static void requireHeaderDiscountCap(BigDecimal subtotal, BigDecimal discount) {
        BigDecimal cap = money(subtotal).multiply(MAX_HEADER_DISCOUNT_RATIO);
        if (money(discount).compareTo(cap) > 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Invoice discount cannot exceed 50% of the subtotal.");
        }
    }

    public static void requirePaymentsCoverTotal(BigDecimal total, List<PaymentRequest> payments) {
        if (payments == null || payments.isEmpty()) {
            return;
        }
        BigDecimal paid = payments.stream()
                .map(p -> money(p.amount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (paid.compareTo(money(total)) > 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Payments cannot exceed the invoice total.");
        }
        for (PaymentRequest payment : payments) {
            requireNonNegative(payment.amount(), "Payment amount");
            if (payment.amount() == null || payment.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Each payment must be positive.");
            }
        }
    }

    public static boolean pricesMatch(BigDecimal authoritative, BigDecimal submitted) {
        if (submitted == null) {
            return true;
        }
        return money(authoritative).subtract(money(submitted)).abs().compareTo(PRICE_EPSILON) <= 0;
    }
}
