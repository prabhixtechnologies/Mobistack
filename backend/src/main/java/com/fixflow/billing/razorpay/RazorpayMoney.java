package com.fixflow.billing.razorpay;

import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class RazorpayMoney {

    public static final long MIN_AMOUNT_PAISE = 100L;

    private RazorpayMoney() {
    }

    public static long toPaise(BigDecimal rupees) {
        if (rupees == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Amount is required.");
        }
        return rupees.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    }

    public static long requireMinimum(long amountPaise) {
        if (amountPaise < MIN_AMOUNT_PAISE) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Amount must be at least " + MIN_AMOUNT_PAISE + " paise.");
        }
        return amountPaise;
    }
}
