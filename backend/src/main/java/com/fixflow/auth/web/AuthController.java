package com.fixflow.auth.web;

import com.fixflow.auth.dto.AuthDtos.AuthenticatedUser;
import com.fixflow.auth.service.AuthService;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.security.CurrentUser;
import com.fixflow.security.jwt.TokenDenyList;
import com.prabhix.identity.client.BearerTokens;
import com.prabhix.identity.client.IdentityClientProperties;
import com.prabhix.identity.client.IdentityToken;
import com.prabhix.identity.client.IdentityTokenVerifier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * The two things a client still asks this API about its session. Everything else about signing in
 * (credentials, OTP, password reset, SSO, device sessions) is Prabhix Identity's: the consoles send
 * people to its hosted pages and come back holding a bearer token.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/mobistack/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final IdentityTokenVerifier tokenVerifier;
    private final IdentityClientProperties identity;
    private final TokenDenyList denyList;

    @GetMapping("/me")
    @Operation(summary = "The signed-in person: selected shop, roles, permissions and plan")
    public AuthenticatedUser me() {
        return authService.currentUser(CurrentUser.userId());
    }

    /**
     * Ends this sign-in at Identity. A local acknowledgement left the cookie able to mint a new
     * access token, so the next page load signed the person straight back in.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Ends this Identity session")
    public void logout(HttpServletRequest request,
                       @org.springframework.web.bind.annotation.RequestBody(required = false) LogoutBody body) {
        String bearer = BearerTokens.from(request);
        if (bearer == null || !identity.enabled()) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Sign in again before signing out.");
        }
        IdentityToken token = tokenVerifier.verify(bearer);
        denyList.revokeSession(token.sessionId());
        String refresh = body == null ? null : body.refreshToken();
        try {
            var call = RestClient.create().post()
                    .uri(identity.issuer() + "/api/v1/identity/auth/logout");
            if (refresh != null && !refresh.isBlank()) {
                call.body(java.util.Map.of("refreshToken", refresh));
            }
            call.header("Authorization", "Bearer " + bearer)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ex) {
            log.warn("Identity did not accept the sign-out: {}", ex.getMessage());
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE,
                    "Sign-out did not finish. Try again.");
        }
    }

    public record LogoutBody(String refreshToken) {
    }
}
