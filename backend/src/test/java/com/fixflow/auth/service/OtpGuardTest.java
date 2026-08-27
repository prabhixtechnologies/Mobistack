package com.fixflow.auth.service;

import com.fixflow.auth.domain.UserToken;
import com.fixflow.auth.repository.UserTokenRepository;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A six-digit code has a million possibilities and lives for ten minutes, which
 * is well within reach of a caller who can keep guessing. These tests hold the
 * two limits that make it a credential rather than a formality: guesses run out,
 * and a fresh code cannot be summoned to reset the count.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtpGuardTest {

    private static final String PHONE = "+919812345678";
    private static final String EMAIL = "owner@shop.test";
    private static final String SMS = "PHONE_OTP";
    private static final String WHATSAPP = "WHATSAPP_OTP";

    @Mock
    private UserTokenRepository userTokenRepository;

    private FixFlowProperties properties;
    private OtpGuard guard;

    @BeforeEach
    void setUp() {
        properties = new FixFlowProperties();
        properties.getAuth().setOtpMaxAttempts(3);
        properties.getAuth().setOtpResendCooldown(Duration.ofSeconds(45));
        guard = new OtpGuard(userTokenRepository, properties);
    }

    private UserToken liveCode(String tokenType, Instant createdAt, int attempts) {
        UserToken token = new UserToken();
        token.setTokenType(tokenType);
        token.setPhone(PHONE);
        token.setCreatedAt(createdAt);
        token.setAttempts((short) attempts);
        return token;
    }

    private void phoneCode(String tokenType, UserToken token) {
        when(userTokenRepository.findFirstByTokenTypeAndPhoneAndUsedAtIsNullOrderByCreatedAtDesc(tokenType, PHONE))
                .thenReturn(Optional.ofNullable(token));
    }

    @Test
    void wrongGuessesAccumulateWithoutBurningTheCodeEarly() {
        UserToken code = liveCode(SMS, Instant.now(), 0);
        phoneCode(SMS, code);

        guard.recordFailure(OtpGuard.Channel.PHONE, PHONE, SMS);

        assertThat(code.getAttempts()).isEqualTo((short) 1);
        assertThat(code.getUsedAt())
                .as("one wrong digit should not lock a shopkeeper out of their own shop")
                .isNull();
        verify(userTokenRepository).save(code);
    }

    @Test
    void codeIsBurnedOnceTheAllowanceIsSpent() {
        UserToken code = liveCode(SMS, Instant.now(), 2);
        phoneCode(SMS, code);

        guard.recordFailure(OtpGuard.Channel.PHONE, PHONE, SMS);

        assertThat(code.getAttempts()).isEqualTo((short) 3);
        assertThat(code.getUsedAt())
                .as("a spent code must stop being accepted, or the guessing simply continues")
                .isNotNull();
    }

    @Test
    void aLimitOfZeroLeavesTheCodeAliveButStillCounts() {
        properties.getAuth().setOtpMaxAttempts(0);
        UserToken code = liveCode(SMS, Instant.now(), 9);
        phoneCode(SMS, code);

        guard.recordFailure(OtpGuard.Channel.PHONE, PHONE, SMS);

        assertThat(code.getUsedAt()).isNull();
        assertThat(code.getAttempts()).isEqualTo((short) 10);
    }

    @Test
    void nothingIsRecordedWhenNoCodeIsOutstanding() {
        phoneCode(SMS, null);

        assertThatCode(() -> guard.recordFailure(OtpGuard.Channel.PHONE, PHONE, SMS)).doesNotThrowAnyException();

        verify(userTokenRepository, never()).save(any());
    }

    @Test
    void resendIsRefusedWhileTheLastCodeIsStillFresh() {
        phoneCode(SMS, liveCode(SMS, Instant.now().minusSeconds(5), 0));

        assertThatThrownBy(() -> guard.requireResendAllowed(OtpGuard.Channel.PHONE, PHONE, SMS))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.RATE_LIMITED)
                .hasMessageContaining("Wait");
    }

    @Test
    void resendIsAllowedOnceTheCooldownHasPassed() {
        phoneCode(SMS, liveCode(SMS, Instant.now().minusSeconds(60), 0));

        assertThatCode(() -> guard.requireResendAllowed(OtpGuard.Channel.PHONE, PHONE, SMS))
                .doesNotThrowAnyException();
    }

    @Test
    void switchingBetweenSmsAndWhatsAppDoesNotDoubleTheAllowance() {
        // The newest code across every channel wins, so a caller cannot alternate
        // SMS and WhatsApp to send a message every few seconds.
        phoneCode(SMS, liveCode(SMS, Instant.now().minusSeconds(90), 0));
        phoneCode(WHATSAPP, liveCode(WHATSAPP, Instant.now().minusSeconds(2), 0));

        assertThatThrownBy(() -> guard.requireResendAllowed(OtpGuard.Channel.PHONE, PHONE, SMS, WHATSAPP))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.RATE_LIMITED);
    }

    @Test
    void aDisabledCooldownLetsEveryResendThrough() {
        properties.getAuth().setOtpResendCooldown(Duration.ZERO);
        phoneCode(SMS, liveCode(SMS, Instant.now(), 0));

        assertThatCode(() -> guard.requireResendAllowed(OtpGuard.Channel.PHONE, PHONE, SMS))
                .doesNotThrowAnyException();
    }

    @Test
    void emailCodesAreCountedOnTheirOwnChannel() {
        UserToken code = new UserToken();
        code.setTokenType("EMAIL_OTP");
        code.setEmail(EMAIL);
        code.setCreatedAt(Instant.now());
        code.setAttempts((short) 2);
        when(userTokenRepository
                .findFirstByTokenTypeAndEmailIgnoreCaseAndUsedAtIsNullOrderByCreatedAtDesc("EMAIL_OTP", EMAIL))
                .thenReturn(Optional.of(code));

        guard.recordFailure(OtpGuard.Channel.EMAIL, EMAIL, "EMAIL_OTP");

        assertThat(code.getUsedAt()).isNotNull();
        verify(userTokenRepository, never())
                .findFirstByTokenTypeAndPhoneAndUsedAtIsNullOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void aBlankDestinationIsIgnoredRatherThanQueried() {
        assertThatCode(() -> guard.requireResendAllowed(OtpGuard.Channel.PHONE, "  ", SMS))
                .doesNotThrowAnyException();

        verify(userTokenRepository, never())
                .findFirstByTokenTypeAndPhoneAndUsedAtIsNullOrderByCreatedAtDesc(any(), any());
    }
}
