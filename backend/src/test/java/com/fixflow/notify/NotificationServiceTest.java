package com.fixflow.notify;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationServiceTest {

    @Test
    void authSecretsAreRecognized() {
        assertThat(NotificationService.isAuthSecret("PASSWORD_RESET")).isTrue();
        assertThat(NotificationService.isAuthSecret("EMAIL_OTP")).isTrue();
        assertThat(NotificationService.isAuthSecret("PHONE_OTP")).isTrue();
        assertThat(NotificationService.isAuthSecret("WHATSAPP_OTP")).isTrue();
        assertThat(NotificationService.isAuthSecret("MAGIC_LINK")).isTrue();
        assertThat(NotificationService.isAuthSecret("SALE_COMPLETED")).isFalse();
    }

    @Test
    void redactedBodyNeverKeepsResetTokensOrOtps() {
        assertThat(NotificationService.redactedBody("PASSWORD_RESET", "Reset token: super-secret"))
                .isEqualTo("Delivered through a private channel.")
                .doesNotContain("super-secret");
        assertThat(NotificationService.redactedBody("EMAIL_OTP", "OTP: 123456"))
                .doesNotContain("123456");
        assertThat(NotificationService.redactedBody("SALE_COMPLETED", "Invoice 12"))
                .isEqualTo("Invoice 12");
    }
}
