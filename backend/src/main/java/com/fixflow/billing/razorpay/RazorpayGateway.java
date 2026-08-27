package com.fixflow.billing.razorpay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RazorpayGateway {

    public record CreatedOrder(String id, long amount, String currency) {
    }

    private static final String ORDERS_URL = "https://api.razorpay.com/v1/orders";

    private final FixFlowProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean configured() {
        return properties.getRazorpay().configured();
    }

    public String keyId() {
        return properties.getRazorpay().getKeyId();
    }

    public CreatedOrder createOrder(long amountPaise, String currency, String receipt, Map<String, String> notes) {
        RazorpayMoney.requireMinimum(amountPaise);
        if (!configured()) {
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE, "Razorpay is not configured.");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amount", amountPaise);
        payload.put("currency", currency == null || currency.isBlank() ? "INR" : currency);
        payload.put("receipt", receipt);
        if (notes != null && !notes.isEmpty()) {
            payload.put("notes", notes);
        }
        try {
            return restClient().post()
                    .uri(ORDERS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        JsonNode body = objectMapper.readTree(response.getBody());
                        if (status == 401 || status == 403) {
                            log.warn("Razorpay rejected the API keys with HTTP {}", status);
                            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE,
                                    "Razorpay rejected the API keys. In the Razorpay dashboard (Test Mode) generate a new Key Id and Key Secret, put both in .env, and recreate the backend.");
                        }
                        if (status >= 400 || body == null || !body.hasNonNull("id")) {
                            String description = body == null ? "" : body.path("error").path("description").asText("");
                            log.warn("Razorpay order create failed with HTTP {} {}", status, description);
                            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not create a Razorpay order.");
                        }
                        String id = body.get("id").asText();
                        if (!id.startsWith("order_")) {
                            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not create a Razorpay order.");
                        }
                        log.info("Created Razorpay order {}", id);
                        return new CreatedOrder(id, body.path("amount").asLong(amountPaise),
                                body.path("currency").asText("INR"));
                    });
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Razorpay order create failed: {}", ex.getMessage());
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not create a Razorpay order.");
        }
    }

    public boolean verifyCheckoutSignature(String orderId, String paymentId, String signature) {
        return RazorpaySignature.matches(orderId, paymentId, signature, properties.getRazorpay().getKeySecret());
    }

    public boolean webhooksConfigured() {
        String secret = properties.getRazorpay().getWebhookSecret();
        return secret != null && !secret.isBlank();
    }

    public boolean verifyWebhookSignature(String rawBody, String signature) {
        return RazorpaySignature.matchesWebhook(rawBody, signature,
                properties.getRazorpay().getWebhookSecret());
    }

    private RestClient restClient() {
        return RestClient.builder()
                .defaultHeaders(headers -> headers.setBasicAuth(keyId(), properties.getRazorpay().getKeySecret()))
                .build();
    }
}
