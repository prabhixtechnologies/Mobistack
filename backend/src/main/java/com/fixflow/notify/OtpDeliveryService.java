package com.fixflow.notify;

import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OtpDeliveryService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TwilioGateway twilio;
    private final NotificationService notificationService;
    private final FixFlowProperties properties;
    private final Environment environment;

    public record Delivery(String code, String hint, String channel, String provider, boolean live) {
    }

    public String normalizePhone(String phone) {
        return twilio.toE164(phone);
    }

    public Delivery sendPhone(String phone, boolean whatsapp, UUID userId) {
        boolean live = whatsapp ? twilio.canWhatsapp() : twilio.canSms();
        String product = properties.getBrand().getProduct();
        String code = issueCode();
        String body = "Your " + product + " code is " + code + ". It expires in 10 minutes. — " + product;
        String event = whatsapp ? "WHATSAPP_OTP" : "PHONE_OTP";

        if (whatsapp) {
            if (twilio.credentialsPresent() && !twilio.canWhatsapp()) {
                throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE,
                        "Twilio credentials are set but TWILIO_WHATSAPP_FROM is missing.");
            }
            if (live) {
                String sid = twilio.sendWhatsapp(phone, body);
                notificationService.record(null, userId, event, "WHATSAPP", twilio.toE164(phone),
                        "Your " + product + " code", "OTP delivered", "SENT", "twilio", sid);
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
                        "Your " + product + " code", "OTP delivered", "SENT", "twilio", sid);
                return new Delivery(code, "A text message was sent.", "SMS", "twilio", true);
            }
        }

        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE,
                    whatsapp
                            ? "WhatsApp sign-in is not configured on this server."
                            : "SMS sign-in is not configured on this server.");
        }

        notificationService.emit(null, userId, event, phone, "Your " + product + " code",
                "A one-time code was issued. The secret is not stored in the shop inbox.");
        return new Delivery(code, "A one-time code was sent.",
                whatsapp ? "WHATSAPP" : "SMS", "log", false);
    }

    private String issueCode() {
        String configured = properties.getAuth().getDevOtp();
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
