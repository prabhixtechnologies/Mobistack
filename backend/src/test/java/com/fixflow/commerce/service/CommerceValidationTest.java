package com.fixflow.commerce.service;

import com.fixflow.commerce.dto.CommerceDtos.PaymentRequest;
import com.fixflow.commerce.domain.PaymentMethod;
import com.fixflow.common.error.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommerceValidationTest {

    @Test
    void rejectsDiscountAboveHalfTheLine() {
        assertThatThrownBy(() -> CommerceValidation.requireLineDiscountCap(
                new BigDecimal("100"), 2, new BigDecimal("150")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsPaymentsAboveTotal() {
        assertThatThrownBy(() -> CommerceValidation.requirePaymentsCoverTotal(
                new BigDecimal("100"),
                List.of(new PaymentRequest(PaymentMethod.CASH, new BigDecimal("150"), null))))
                .isInstanceOf(ApiException.class);
    }
}
