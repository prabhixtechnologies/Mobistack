package com.fixflow.billing.razorpay;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Razorpay Checkout success signature:
 * HMAC-SHA256(order_id + "|" + payment_id, KEY_SECRET)
 */
public final class RazorpaySignature {

    private RazorpaySignature() {
    }

    public static boolean matches(String orderId, String paymentId, String signature, String keySecret) {
        if (isBlank(orderId) || isBlank(paymentId) || isBlank(signature) || isBlank(keySecret)) {
            return false;
        }
        String expected = hmacSha256Hex(orderId + "|" + paymentId, keySecret);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Razorpay webhook signature: HMAC-SHA256 of the exact raw body, keyed with
     * the webhook secret — which is a different secret from the API key.
     *
     * <p>The body must be the bytes as received. Parsing and re-serialising the
     * JSON changes whitespace and key order, and the signature stops matching.
     */
    public static boolean matchesWebhook(String rawBody, String signature, String webhookSecret) {
        if (isBlank(rawBody) || isBlank(signature) || isBlank(webhookSecret)) {
            return false;
        }
        String expected = hmacSha256Hex(rawBody, webhookSecret);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    public static String hmacSha256Hex(String payload, String keySecret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not compute Razorpay signature", ex);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
