package com.fixflow.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "fixflow")
public class FixFlowProperties {

    private final Security security = new Security();
    private final Cors cors = new Cors();
    private final Inventory inventory = new Inventory();
    private final Demo demo = new Demo();
    private final Brand brand = new Brand();
    private final Auth auth = new Auth();
    private final Twilio twilio = new Twilio();
    private final Platform platform = new Platform();
    private final Redis redis = new Redis();
    private final Devices devices = new Devices();
    private final Chat chat = new Chat();
    private final Updates updates = new Updates();
    private final Razorpay razorpay = new Razorpay();

    @Getter
    @Setter
    public static class Security {
        private final Jwt jwt = new Jwt();

        /** Consecutive failed logins before the account is temporarily locked. */
        private int maxFailedLogins = 8;
        private Duration lockoutDuration = Duration.ofMinutes(15);
    }

    @Getter
    @Setter
    public static class Jwt {
        /**
         * HMAC signing key. Must be at least 64 characters. There is deliberately no
         * usable default: the app refuses to start in a non-dev profile without one.
         */
        @NotBlank
        private String secret;

        private String issuer = "mobistack";
        private Duration accessTokenTtl = Duration.ofMinutes(30);
        /** Long enough that a shop tablet offline for a week can still refresh. */
        private Duration refreshTokenTtl = Duration.ofDays(30);
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = List.of(
                "https://mobistack.prabhixtechnologies.com",
                "https://www.mobistack.prabhixtechnologies.com",
                "http://localhost:5173",
                "http://localhost:4173",
                "http://localhost:8081",
                "http://localhost:19006"
        );
    }

    @Getter
    @Setter
    public static class Inventory {
        /** A variant with stock but no sale in this many days counts as dead stock. */
        @Positive
        private int deadStockDays = 120;
        /** Stock at or below reorderLevel * this factor is critical (red) rather than low. */
        private double criticalStockFactor = 0.34;
    }

    @Getter
    @Setter
    public static class Brand {
        private String organization = "Prabhix Technologies Pvt Ltd";
        private String product = "MobiStack";
        private String tagline = "Building software that simplifies business";
        private int copyrightYear = 2026;
    }

    @Getter
    @Setter
    public static class Auth {
        /** Public web origin used to build magic links. Overridden in prod. */
        private String webOrigin = "https://mobistack.prabhixtechnologies.com";
        private String magicLinkPath = "/login?magic=";
        /** Dev/local one-time codes. Production replaces this with a real sender. */
        private String devOtp = "123456";
        private boolean devSsoEnabled = true;
        private String googleClientId = "";
        private String googleClientSecret = "";
        private String googleRedirectPath = "/login?sso=google";
    }

    @Getter
    @Setter
    public static class Twilio {
        private String accountSid = "";
        private String authToken = "";
        /** E.164 SMS sender, e.g. +14155552671 */
        private String smsFrom = "";
        /** WhatsApp sender, e.g. whatsapp:+14155238886 for the Twilio sandbox */
        private String whatsappFrom = "";
        /** Used when the user types a 10-digit local number. */
        private String defaultCountryCode = "91";
    }

    @Getter
    @Setter
    public static class Demo {
        /** Seeds a realistic demo shop on an empty database. Off outside the dev profile. */
        private boolean seedEnabled = false;
        private String ownerEmail = "owner@prabhixtechnologies.com";
        private String ownerPassword = "Owner@123";
    }

    @Getter
    @Setter
    public static class Platform {
        private String publicOrigin = "https://mobistack.prabhixtechnologies.com";
        private String apiOrigin = "https://mobistack.prabhixtechnologies.com";
        private String supportEmail = "support@prabhixtechnologies.com";
        private String supportPhone = "";
        private boolean requireHttps = false;
    }

    @Getter
    @Setter
    public static class Redis {
        private boolean enabled = false;
        private Duration presenceTtl = Duration.ofSeconds(90);
        private Duration dashboardTtl = Duration.ofSeconds(20);
    }

    @Getter
    @Setter
    public static class Devices {
        private int defaultMaxPerUser = 3;
        @Positive
        private int absoluteMaxPerUser = 20;
        /** revoke-oldest keeps the shop working; reject forces the user to drop a device. */
        private String overLimit = "revoke-oldest";
    }

    @Getter
    @Setter
    public static class Chat {
        private String openaiApiKey = "";
        private String openaiModel = "gpt-4o-mini";
    }

    @Getter
    @Setter
    public static class Razorpay {
        /** Public checkout key. Safe to send to the browser. */
        private String keyId = "";
        /** Server-only signing secret. Never returned from an API. */
        private String keySecret = "";

        public boolean configured() {
            return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
        }
    }

    @Getter
    @Setter
    public static class Updates {
        private String expoUpdatesUrl = "";
        private String androidDownloadUrl = "https://mobistack.prabhixtechnologies.com/app/android";
        private String iosDownloadUrl = "https://mobistack.prabhixtechnologies.com/app/ios";
    }
}
