package com.fixflow.config;

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

    private final Cors cors = new Cors();
    private final Inventory inventory = new Inventory();
    private final Demo demo = new Demo();
    private final Brand brand = new Brand();
    private final Auth auth = new Auth();
    private final Platform platform = new Platform();
    private final Redis redis = new Redis();
    private final Chat chat = new Chat();
    private final Updates updates = new Updates();
    private final Razorpay razorpay = new Razorpay();
    private final Mail mail = new Mail();
    private final Push push = new Push();

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = List.of(
                "https://mobistack.prabhixtechnologies.com",
                "https://www.mobistack.prabhixtechnologies.com",
                "http://localhost:5173",
                "http://localhost:5176",
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

    /**
     * What little this API still knows about sign-in. Credentials, sessions and passwords belong to
     * Prabhix Identity; a bearer token is verified there and MobiStack only resolves what the
     * person may do in the shop they selected.
     */
    @Getter
    @Setter
    public static class Auth {
        /** Public web origin of the SPA, allowed by CORS and reported by /public/brand. */
        private String webOrigin = "https://mobistack.prabhixtechnologies.com";

        /**
         * A fixed development OTP. Blank in every real profile: a value here would accept that code
         * for every number. Sign-in is Identity's, so this stays empty.
         */
        private String devOtp = "";

        /** A development SSO bypass. Off. This API never signs someone in by itself. */
        private boolean devSsoEnabled = false;
    }

    @Getter
    @Setter
    public static class Demo {
        /** Seeds a realistic demo shop on an empty database. Off outside the dev profile. */
        private boolean seedEnabled = false;
        /**
         * The owner row is a placeholder mirror: it is linked to the person's Identity account by
         * email the first time they sign in, so this must be the address of an account there.
         */
        private String ownerEmail = "owner@prabhixtechnologies.com";
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
        private String androidDownloadUrl = "https://store.prabhixtechnologies.com/mobistack/android.apk";
        private String iosDownloadUrl = "https://mobistack.prabhixtechnologies.com/download/ios";
        /** Legacy local path; Android is no longer served from this host. */
        private String androidApkPath = "/var/mobistack/downloads/MobiStack.apk";
        /** File served at /download/ios. Directory is also accepted. */
        private String iosIpaPath = "/var/mobistack/downloads/MobiStack.ipa";
    }
}
