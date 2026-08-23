package com.fixflow.notify;

import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OtpDeliveryService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TwilioGateway twilio;
    private final NotificationService notificationService;
    private final FixFlowProperties properties;

    public record Delivery(String code, String hint, String channel, String provider, boolean live) {
    }

    public String normalizePhone(String phone) {
        return twilio.toE164(phone);
    }

    public Delivery sendPhone(String phone, boolean whatsapp, UUID userId) {
        boolean live = whatsapp ? twilio.canWhatsapp() : twilio.canSms();
        String code = live ? randomCode() : properties.getAuth().getDevOtp();
        String body = "Your FixFlow code is " + code + ". It expires in 10 minutes. — "
                + properties.getBrand().getProduct();
        String event = whatsapp ? "WHATSAPP_OTP" : "PHONE_OTP";

        if (whatsapp) {
            if (twilio.credentialsPresent() && !twilio.canWhatsapp()) {
                throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE,
                        "Twilio credentials are set but TWILIO_WHATSAPP_FROM is missing.");
            }
            if (live) {
                String sid = twilio.sendWhatsapp(phone, body);
                notificationService.record(null, userId, event, "WHATSAPP", twilio.toE164(phone),
                        "Your FixFlow code", "OTP delivered", "SENT", "twilio", sid);
                return new Delivery(code, "A WhatsApp message was sent.", "WHATSAPP", "twilio", true);
            }
        } else {
            if (twilio.credentialsPresent() && !twilio.canSms()) {
                throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE,
                        "Twilio credentials are set but TWILIO_SMS_FROM is missing.");
            }
            if (live) {
                String sid = twilio.sendSms(phone, body);
                notificationService.record(null, userId, event, "SMS", twilio.toE164(phone),
                        "Your FixFlow code", "OTP delivered", "SENT", "twilio", sid);
                return new Delivery(code, "A text message was sent.", "SMS", "twilio", true);
            }
        }

        notificationService.emit(null, userId, event, phone, "Your FixFlow code",
                "OTP: " + code + " (dev provider — Twilio is not configured)");
        return new Delivery(code, "Dev code is " + code + ". Set Twilio to send a real " +
                (whatsapp ? "WhatsApp" : "SMS") + " message.",
                whatsapp ? "WHATSAPP" : "SMS", "log", false);
    }

    private static String randomCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
