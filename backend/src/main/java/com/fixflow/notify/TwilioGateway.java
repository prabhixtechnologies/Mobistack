package com.fixflow.notify;

import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Twilio Programmable Messaging. SMS uses a regular From number. WhatsApp uses
 * a WhatsApp-enabled sender (sandbox or a purchased sender).
 */
@Slf4j
@Component
public class TwilioGateway {

    private final FixFlowProperties.Twilio twilio;
    private final RestClient http;

    public TwilioGateway(FixFlowProperties properties) {
        this.twilio = properties.getTwilio();
        this.http = RestClient.builder()
                .baseUrl("https://api.twilio.com")
                .build();
    }

    public boolean credentialsPresent() {
        return StringUtils.hasText(twilio.getAccountSid()) && StringUtils.hasText(twilio.getAuthToken());
    }

    public boolean canSms() {
        return credentialsPresent() && StringUtils.hasText(twilio.getSmsFrom());
    }

    public boolean canWhatsapp() {
        return credentialsPresent() && StringUtils.hasText(twilio.getWhatsappFrom());
    }

    public String sendSms(String phone, String body) {
        if (!canSms()) {
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE,
                    "Twilio SMS is not configured. Set TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN and TWILIO_SMS_FROM.");
        }
        return send(toE164(phone), twilio.getSmsFrom(), body);
    }

    public String sendWhatsapp(String phone, String body) {
        if (!canWhatsapp()) {
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE,
                    "Twilio WhatsApp is not configured. Set TWILIO_WHATSAPP_FROM (e.g. whatsapp:+14155238886).");
        }
        return send(whatsappAddress(phone), whatsappFrom(), body);
    }

    public String toE164(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Phone number is required.");
        }
        String trimmed = raw.trim().replaceAll("^(?i)whatsapp:", "");
        String compact = trimmed.replaceAll("[\\s()-]", "");
        if (compact.startsWith("00")) {
            compact = "+" + compact.substring(2);
        }
        if (compact.startsWith("+")) {
            String digits = compact.substring(1).replaceAll("\\D", "");
            if (digits.length() < 8 || digits.length() > 15) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "That phone number is not valid.");
            }
            return "+" + digits;
        }
        String digits = compact.replaceAll("\\D", "");
        if (digits.length() == 10) {
            return "+" + twilio.getDefaultCountryCode() + digits;
        }
        if (digits.length() == 11 && digits.startsWith("0")) {
            return "+" + twilio.getDefaultCountryCode() + digits.substring(1);
        }
        if (digits.length() == 12 && digits.startsWith(twilio.getDefaultCountryCode())) {
            return "+" + digits;
        }
        if (digits.length() >= 8 && digits.length() <= 15) {
            return "+" + digits;
        }
        throw new ApiException(ErrorCode.VALIDATION_FAILED, "That phone number is not valid.");
    }

    private String whatsappAddress(String phone) {
        return "whatsapp:" + toE164(phone);
    }

    private String whatsappFrom() {
        String from = twilio.getWhatsappFrom().trim();
        return from.startsWith("whatsapp:") ? from : "whatsapp:" + from;
    }

    private String send(String to, String from, String body) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("To", to);
        form.add("From", from);
        form.add("Body", body);
        try {
            TwilioMessageResponse response = http.post()
                    .uri("/2010-04-01/Accounts/{sid}/Messages.json", twilio.getAccountSid())
                    .headers(headers -> headers.setBasicAuth(twilio.getAccountSid(), twilio.getAuthToken()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TwilioMessageResponse.class);
            String sid = response == null ? null : response.sid();
            log.info("Twilio accepted message {} to {}", sid, to);
            return sid;
        } catch (RestClientResponseException ex) {
            log.warn("Twilio rejected message to {}: {}", to, ex.getResponseBodyAsString());
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE,
                    "Twilio could not send that message. Check the number, sender, and WhatsApp sandbox join.");
        }
    }

    public record TwilioMessageResponse(String sid, String status) {
    }
}
