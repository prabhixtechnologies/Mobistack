package com.fixflow.billing.razorpay;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class RazorpaySignatureTest {

    @Test
    void acceptsMatchingHmac() throws Exception {
        String expected = independentHmac("order_test1|pay_test1", "test-secret");
        assertThat(RazorpaySignature.matches("order_test1", "pay_test1", expected, "test-secret")).isTrue();
    }

    @Test
    void rejectsMismatchedSignature() {
        assertThat(RazorpaySignature.matches("order_test1", "pay_test1", "deadbeef", "test-secret")).isFalse();
    }

    @Test
    void rejectsMissingFields() {
        String signature = RazorpaySignature.hmacSha256Hex("order_test1|pay_test1", "test-secret");
        assertThat(RazorpaySignature.matches("", "pay_test1", signature, "test-secret")).isFalse();
        assertThat(RazorpaySignature.matches("order_test1", "", signature, "test-secret")).isFalse();
        assertThat(RazorpaySignature.matches("order_test1", "pay_test1", "", "test-secret")).isFalse();
        assertThat(RazorpaySignature.matches("order_test1", "pay_test1", signature, "")).isFalse();
    }

    @Test
    void convertsRupeesToPaise() {
        assertThat(RazorpayMoney.toPaise(new java.math.BigDecimal("50.00"))).isEqualTo(5000L);
        assertThat(RazorpayMoney.requireMinimum(100L)).isEqualTo(100L);
    }

    @Test
    void rejectsAmountBelowMinimum() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> RazorpayMoney.requireMinimum(99L))
                .isInstanceOf(com.fixflow.common.error.ApiException.class);
    }

    private static String independentHmac(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
