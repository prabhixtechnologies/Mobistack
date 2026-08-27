package com.fixflow.auth.service;

import com.fixflow.auth.domain.UserToken;
import com.fixflow.auth.repository.UserTokenRepository;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Makes a six-digit code safe to use as a credential.
 *
 * <p>Two limits do that. A code is burned after a few wrong guesses, so the
 * six-digit space cannot be walked inside the code's ten-minute life; and a new
 * code cannot be requested straight away, so the guess counter cannot be reset
 * in a loop. The per-IP rate limit stopped neither on its own, because a caller
 * with a spread of addresses stays under it while aiming everything at one
 * number.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpGuard {

    private final UserTokenRepository userTokenRepository;
    private final FixFlowProperties properties;

    /** Where a code was sent. Email and phone are counted separately. */
    public enum Channel { EMAIL, PHONE }

    /**
     * Refuses a resend that arrives too soon after the last one.
     *
     * <p>Also protects the recipient. Without it the endpoint is a way to send an
     * unlimited run of texts to a number the caller does not own.
     *
     * <p>Takes every token type that can reach the destination, so alternating
     * SMS and WhatsApp does not double the allowance.
     */
    @Transactional(readOnly = true)
    public void requireResendAllowed(Channel channel, String destination, String... tokenTypes) {
        Duration cooldown = properties.getAuth().getOtpResendCooldown();
        if (cooldown == null || cooldown.isZero() || cooldown.isNegative()) {
            return;
        }
        newest(channel, destination, tokenTypes).ifPresent(row -> {
            Instant nextAllowed = row.getCreatedAt().plus(cooldown);
            Instant now = Instant.now();
            if (nextAllowed.isAfter(now)) {
                long wait = Math.max(1, Duration.between(now, nextAllowed).toSeconds());
                throw new ApiException(ErrorCode.RATE_LIMITED,
                        "A code was just sent. Wait %d seconds before asking for another.".formatted(wait));
            }
        });
    }

    /**
     * Records a wrong guess and burns the code once the allowance is spent.
     *
     * <p>Runs in its own transaction because the caller is about to throw, and
     * the rollback that follows would otherwise discard the count.
     *
     * <p>The caller's error message stays the same however the code failed.
     * Reporting "too many attempts" only for numbers with a code outstanding
     * would confirm which numbers those are.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Channel channel, String destination, String... tokenTypes) {
        int max = properties.getAuth().getOtpMaxAttempts();
        newest(channel, destination, tokenTypes).ifPresent(row -> {
            row.setAttempts((short) (row.getAttempts() + 1));
            if (max > 0 && row.getAttempts() >= max) {
                row.setUsedAt(Instant.now());
                log.info("Burned a {} code after {} wrong attempts", row.getTokenType(), row.getAttempts());
            }
            userTokenRepository.save(row);
        });
    }

    private Optional<UserToken> newest(Channel channel, String destination, String... tokenTypes) {
        if (destination == null || destination.isBlank() || tokenTypes.length == 0) {
            return Optional.empty();
        }
        return Stream.of(tokenTypes)
                .map(type -> channel == Channel.EMAIL
                        ? userTokenRepository
                        .findFirstByTokenTypeAndEmailIgnoreCaseAndUsedAtIsNullOrderByCreatedAtDesc(type, destination)
                        : userTokenRepository
                        .findFirstByTokenTypeAndPhoneAndUsedAtIsNullOrderByCreatedAtDesc(type, destination))
                .flatMap(Optional::stream)
                .max(Comparator.comparing(UserToken::getCreatedAt));
    }
}
