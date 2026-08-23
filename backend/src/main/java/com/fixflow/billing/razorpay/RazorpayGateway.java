package com.fixflow.billing.razorpay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
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
            JsonNode body = restClient().post()
                    .uri(ORDERS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .onStatus(status -> status.value() == 401, (request, response) -> {
                        throw new ApiException(ErrorCode.UNAUTHENTICATED, "Razorpay authentication failed.");
                    })
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        log.warn("Razorpay order create failed with HTTP {}", response.getStatusCode().value());
                        throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not create a Razorpay order.");
                    })
                    .body(JsonNode.class);
            if (body == null || !body.hasNonNull("id")) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not create a Razorpay order.");
            }
            String id = body.get("id").asText();
            log.info("Created Razorpay order {}", id);
            return new CreatedOrder(id, body.path("amount").asLong(amountPaise), body.path("currency").asText("INR"));
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

    private RestClient restClient() {
        return RestClient.builder()
                .defaultHeaders(headers -> headers.setBasicAuth(keyId(), properties.getRazorpay().getKeySecret()))
                .build();
    }
}
