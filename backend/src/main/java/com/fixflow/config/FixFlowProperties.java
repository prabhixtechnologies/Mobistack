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
    private final Mail mail = new Mail();
    private final Push push = new Push();

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
        private String tagline = "See what fits. See stock. Sell.";
        private String organizationTagline = "Building software that simplifies business";
        private int copyrightYear = 2026;
    }

    @Getter
    @Setter
    public static class Auth {
        /** Public web origin used to build magic links. Overridden in prod. */
        private String webOrigin = "https://mobistack.prabhixtechnologies.com";
        private String magicLinkPath = "/login?magic=";
        /**
         * Fixed one-time code so a developer can sign in without a mail or SMS
         * account. Blank everywhere except the dev profile: a value here accepts
         * that code for every number in the system.
         */
        private String devOtp = "";
        /** Password-free sign-in as any user. Dev profile only, for the same reason. */
        private boolean devSsoEnabled = false;
        private String googleClientId = "";
        private String googleClientSecret = "";
        private String googleRedirectPath = "/login?sso=google";
        /** Wrong guesses a one-time code survives before it is burned. */
        private int otpMaxAttempts = 5;
        /** How long a caller must wait before a fresh code can be sent. */
        private Duration otpResendCooldown = Duration.ofSeconds(45);
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
        /** Devices one person may stay signed in on when the shop has no override. */
        private int defaultMaxPerUser = 3;
        /** Hard ceiling, so a shop setting cannot raise the cap without limit. */
        @Positive
        private int absoluteMaxPerUser = 5;
        /** {@code evict} ends the least recently used device; {@code reject} refuses the sign-in. */
        private String overLimit = "evict";
    }

    @Getter
    @Setter
    public static class Chat {
        private String openaiApiKey = "";
        private String openaiModel = "gpt-4o-mini";
    }

    @Getter
    @Setter
    public static class Mail {
        private String host = "";
        private String from = "";
        private String username = "";

        public boolean configured() {
            return host != null && !host.isBlank() && from != null && !from.isBlank();
        }
    }

    @Getter
    @Setter
    public static class Push {
        /**
         * Expo access token. Optional, but without it anyone who learns a push
         * token could send alerts that look like they came from MobiStack, and
         * Expo applies tighter rate limits to unauthenticated senders.
         */
        private String expoAccessToken = "";
    }

    @Getter
    @Setter
    public static class Razorpay {
        /** Public checkout key. Safe to send to the browser. */
        private String keyId = "";
        /** Server-only signing secret. Never returned from an API. */
        private String keySecret = "";
        /**
         * Webhook signing secret, set separately in the Razorpay dashboard. It is
         * not the same value as the key secret, and without it a webhook cannot
         * be trusted — so an unsigned one is refused rather than believed.
         */
        private String webhookSecret = "";

        public boolean configured() {
            return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
        }
    }

    @Getter
    @Setter
    public static class Updates {
        private String expoUpdatesUrl = "";
        private String androidDownloadUrl = "https://mobistack.prabhixtechnologies.com/download/android";
        private String iosDownloadUrl = "https://mobistack.prabhixtechnologies.com/download/ios";
        /** File served at /download/android. Directory is also accepted. */
        private String androidApkPath = "/var/mobistack/downloads/MobiStack.apk";
        /** File served at /download/ios. Directory is also accepted. */
        private String iosIpaPath = "/var/mobistack/downloads/MobiStack.ipa";
    }
}
