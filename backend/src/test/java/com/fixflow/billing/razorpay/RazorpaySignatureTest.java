package com.fixflow.billing.razorpay;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The webhook signature is the only thing standing between "Razorpay says this
 * shop paid" and "anyone on the internet says this shop paid", so each way it
 * can be wrong is pinned down here.
 */
class RazorpaySignatureTest {

    private static final String SECRET = "whsec-mobistack-test";
    private static final String BODY = """
            {"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_1","order_id":"order_1"}}}}""";

    @Test
    void acceptsABodySignedWithTheWebhookSecret() {
        String signature = RazorpaySignature.hmacSha256Hex(BODY, SECRET);

        assertThat(RazorpaySignature.matchesWebhook(BODY, signature, SECRET)).isTrue();
    }

    @Test
    void rejectsASignatureMadeWithADifferentSecret() {
        String signature = RazorpaySignature.hmacSha256Hex(BODY, "some-other-secret");

        assertThat(RazorpaySignature.matchesWebhook(BODY, signature, SECRET)).isFalse();
    }

    @Test
    void rejectsABodyThatWasChangedAfterSigning() {
        String signature = RazorpaySignature.hmacSha256Hex(BODY, SECRET);
        String tampered = BODY.replace("order_1", "order_2");

        assertThat(RazorpaySignature.matchesWebhook(tampered, signature, SECRET)).isFalse();
    }

    @Test
    void refusesToVerifyWhenNoWebhookSecretIsConfigured() {
        String signature = RazorpaySignature.hmacSha256Hex(BODY, SECRET);

        // An unset secret must never be treated as "no check required".
        assertThat(RazorpaySignature.matchesWebhook(BODY, signature, "")).isFalse();
        assertThat(RazorpaySignature.matchesWebhook(BODY, signature, null)).isFalse();
    }

    @Test
    void refusesAnEmptySignature() {
        assertThat(RazorpaySignature.matchesWebhook(BODY, "", SECRET)).isFalse();
        assertThat(RazorpaySignature.matchesWebhook(BODY, null, SECRET)).isFalse();
    }

    @Test
    void checkoutSignatureCoversTheOrderAndPaymentPair() {
        String good = RazorpaySignature.hmacSha256Hex("order_1|pay_1", SECRET);

        assertThat(RazorpaySignature.matches("order_1", "pay_1", good, SECRET)).isTrue();
        // Reusing a signature against a different order must not pass.
        assertThat(RazorpaySignature.matches("order_2", "pay_1", good, SECRET)).isFalse();
    }
}
