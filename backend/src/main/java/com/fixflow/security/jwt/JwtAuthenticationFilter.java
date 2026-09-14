package com.fixflow.security.jwt;

import tools.jackson.databind.ObjectMapper;
import com.fixflow.common.error.ApiError;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.security.UserPrincipal;
import com.fixflow.user.domain.User;
import com.fixflow.user.repository.UserRepository;
import com.fixflow.workspace.service.WorkspaceAccessService;
import com.prabhix.identity.client.BearerTokens;
import com.prabhix.identity.client.IdentityClientException;
import com.prabhix.identity.client.IdentityToken;
import com.prabhix.identity.client.IdentityTokenException;
import com.prabhix.identity.client.IdentityTokenVerifier;
import com.prabhix.identity.client.IdentityUserMirror;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns a Prabhix Identity bearer token into a MobiStack principal.
 *
 * <p>The token proves who is calling and nothing more: Identity signs it RS256 and this filter only
 * verifies it against the published keys, so no MobiStack process can mint one. Everything about
 * what the caller may do is resolved here from this database: the mirrored user row, the shop
 * named by {@code X-MobiStack-Workspace}, and the permissions of their role in it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String WORKSPACE_HEADER = "X-MobiStack-Workspace";

    /** Service-to-service routes authenticate with the shared token, never with a bearer. */
    private static final String INTERNAL_PREFIX = "/internal/";
    /**
     * Platform admin is the oneOps BFF's, over the service token. A shop JWT must not mint
     * a principal here even if the owner once held {@code system_admin}.
     */
    private static final String ADMIN_PREFIX = "/api/v1/admin";

    private final IdentityTokenVerifier tokenVerifier;
    private final UserRepository userRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final IdentityUserMirror identityUserMirror;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = BearerTokens.from(request);
        if (token == null || isInternal(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            IdentityToken identity = verify(token);
            UserPrincipal principal = authorizeIdentityToken(identity, request);

            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (ApiException ex) {
            SecurityContextHolder.clearContext();
            // Anonymous routes must still run when the browser still has an expired or replaced
            // access token in storage.
            if (isAnonymousOk(request)) {
                filterChain.doFilter(request, response);
                return;
            }
            writeError(request, response, ex);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Maps the verifier's refusal onto the error vocabulary the clients already handle. EXPIRED is
     * the one they act on (silent re-login through Identity); the rest are a sign-out.
     */
    private IdentityToken verify(String token) {
        try {
            return tokenVerifier.verify(token);
        } catch (IdentityTokenException ex) {
            throw switch (ex.reason()) {
                case EXPIRED -> new ApiException(ErrorCode.TOKEN_EXPIRED, "Your session has expired.");
                case INVALID -> new ApiException(ErrorCode.TOKEN_INVALID, "That token is not valid.");
                case UNTRUSTED -> new ApiException(ErrorCode.UNAUTHENTICATED,
                        "This deployment does not trust an identity issuer.");
            };
        }
    }

    /**
     * Builds authority for an identity token, which carries none of its own.
     *
     * <p>Subject is preferred; email is the fallback for accounts that already existed in Identity
     * under a different id (platform imports). Absent entirely means Identity knows this person and
     * this database has not been told yet, which is ordinary for anyone who signs up after the bulk
     * import, so {@link IdentityUserMirror} fills the row once rather than treating it as a
     * credential failure. Workspace and permissions still come from this database.
     */
    private UserPrincipal authorizeIdentityToken(IdentityToken token, HttpServletRequest request) {
        User user = userRepository.findWithRolesById(token.subject())
                .or(() -> byEmail(token.email()))
                .orElseGet(() -> mirror(token.subject()));

        if (!user.isActive()) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "This account is not active");
        }

        UUID preferred = preferredWorkspace(request, user);
        return workspaceAccessService.principalFor(user, preferred);
    }

    private Optional<User> byEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findWithRolesByEmail(email);
    }

    private User mirror(UUID subject) {
        try {
            identityUserMirror.pull(subject);
        } catch (IdentityClientException ex) {
            // NOT_FOUND: Identity signed a token for someone it will no longer describe. Anything
            // else: Identity could not be asked, and a person with no row cannot be authorized
            // without it. Both are a refusal of this request, not a server error.
            log.warn("Could not mirror identity user {}: {} ({})", subject, ex.getMessage(), ex.kind());
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "This account is not provisioned on MobiStack");
        }
        return userRepository.findWithRolesById(subject)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED,
                        "This account is not provisioned on MobiStack"));
    }

    private UUID preferredWorkspace(HttpServletRequest request, User user) {
        String header = request.getHeader(WORKSPACE_HEADER);
        if (header != null && !header.isBlank()) {
            try {
                return UUID.fromString(header.trim());
            } catch (IllegalArgumentException ex) {
                throw new ApiException(ErrorCode.MALFORMED_REQUEST,
                        WORKSPACE_HEADER + " is not a valid id");
            }
        }
        return user.getShopId();
    }

    private static boolean isInternal(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        return path.startsWith(INTERNAL_PREFIX) || path.equals(ADMIN_PREFIX) || path.startsWith(ADMIN_PREFIX + "/");
    }

    static boolean isAnonymousOk(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        return path.startsWith("/api/v1/public/")
                || path.startsWith("/download/")
                || path.startsWith("/actuator/health");
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response, ApiException ex)
            throws IOException {
        response.setStatus(ex.getCode().status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(ex.getCode(), ex.getMessage(), request.getRequestURI()));
    }
}
