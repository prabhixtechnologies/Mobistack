package com.fixflow.security;

import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.prabhix.identity.client.IdentityToken;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.Instant;

/** Same 15-minute freshness rule as OneOps, for shop billing changes. */
@Component
public class RecentAuthentication {

    public static final String TOKEN_ATTRIBUTE = "prabhix.identityToken";
    public static final Duration MAX_AGE = Duration.ofMinutes(15);

    public void requireFresh() {
        IdentityToken token = current();
        Instant provedAt = token == null ? null : token.authTime();
        if (provedAt == null || provedAt.isBefore(Instant.now().minus(MAX_AGE))) {
            throw new ApiException(ErrorCode.STEP_UP_REQUIRED, "Confirm it is you, then try this again.");
        }
    }

    private static IdentityToken current() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        Object value = request.getAttribute(TOKEN_ATTRIBUTE);
        return value instanceof IdentityToken token ? token : null;
    }
}
