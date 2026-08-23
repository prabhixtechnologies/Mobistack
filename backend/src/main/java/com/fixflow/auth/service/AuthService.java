package com.fixflow.auth.service;

import com.fixflow.audit.service.AuditAction;
import com.fixflow.audit.service.AuditService;
import com.fixflow.auth.domain.RefreshToken;
import com.fixflow.auth.domain.UserToken;
import com.fixflow.auth.dto.AuthDtos.AuthResponse;
import com.fixflow.auth.dto.AuthDtos.AuthenticatedUser;
import com.fixflow.auth.dto.AuthDtos.ChangePasswordRequest;
import com.fixflow.auth.dto.AuthDtos.ForgotPasswordRequest;
import com.fixflow.auth.dto.AuthDtos.LoginRequest;
import com.fixflow.auth.dto.AuthDtos.OtpRequest;
import com.fixflow.auth.dto.AuthDtos.RefreshRequest;
import com.fixflow.auth.dto.AuthDtos.RegisterShopRequest;
import com.fixflow.auth.dto.AuthDtos.ResetPasswordRequest;
import com.fixflow.auth.dto.AuthDtos.VerifyOtpRequest;
import com.fixflow.auth.repository.RefreshTokenRepository;
import com.fixflow.auth.repository.UserTokenRepository;
import com.fixflow.notify.NotificationService;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import com.fixflow.security.Permission;
import com.fixflow.security.UserPrincipal;
import com.fixflow.security.jwt.JwtService;
import com.fixflow.shop.domain.Shop;
import com.fixflow.shop.repository.ShopRepository;
import com.fixflow.shop.service.ShopProvisioningService;
import com.fixflow.user.domain.User;
import com.fixflow.user.repository.UserRepository;
import com.fixflow.workspace.dto.WorkspaceDtos.WorkspaceCard;
import com.fixflow.workspace.service.WorkspaceAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ShopProvisioningService shopProvisioningService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final FixFlowProperties properties;
    private final WorkspaceAccessService workspaceAccessService;
    private final UserTokenRepository userTokenRepository;
    private final NotificationService notificationService;
    private final com.fixflow.notify.OtpDeliveryService otpDeliveryService;
    private final DeviceSessionService deviceSessionService;
    private final com.fixflow.billing.service.BillingService billingService;
    private final com.fixflow.notify.EmailDeliveryService emailDeliveryService;

    public record ClientInfo(String deviceId, String userAgent, String ipAddress) {
        public static ClientInfo unknown() {
            return new ClientInfo(null, null, null);
        }
    }

    @Transactional
    public AuthResponse login(LoginRequest request, ClientInfo client) {
        User user = userRepository.findWithRolesByEmail(request.email())
                // Same message for unknown email and wrong password: do not let
                // an attacker enumerate which accounts exist.
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS,
                        "Email or password is incorrect."));

        if (user.isLocked()) {
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED,
                    "Too many failed attempts. Try again later.");
        }
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED, "This account has been deactivated.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailedLogin(user);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Email or password is incorrect.");
        }

        user.setFailedLogins(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        UserPrincipal principal = workspaceAccessService.principalFor(user, user.getShopId());
        if (principal.getShopId() != null) {
            auditService.recordForShop(principal.getShopId(), user.getFullName(), AuditAction.LOGIN_SUCCEEDED,
                    "User", user.getId(), "%s signed in".formatted(user.getFullName()));
        }

        return issueTokens(user, principal, client);
    }

    @Transactional
    public AuthResponse register(RegisterShopRequest request, ClientInfo client) {
        var provisioned = shopProvisioningService.provision(new ShopProvisioningService.NewShop(
                request.shopName(), request.ownerName(), request.email(), request.password(),
                request.phone(), request.city()));

        User owner = userRepository.findWithRolesById(provisioned.owner().getId())
                .orElseThrow(() -> ApiException.notFound("User", provisioned.owner().getId()));

        UserPrincipal principal = workspaceAccessService.principalFor(owner, provisioned.shop().getId());
        try {
            String code = otpDeliveryService.issueCode();
            UserToken row = new UserToken();
            row.setUserId(owner.getId());
            row.setEmail(owner.getEmail());
            row.setTokenType(UserToken.EMAIL_OTP);
            row.setTokenHash(jwtService.hashRefreshToken(code));
            row.setExpiresAt(Instant.now().plus(java.time.Duration.ofMinutes(10)));
            userTokenRepository.save(row);
            emailDeliveryService.send(provisioned.shop().getId(), owner.getId(), "EMAIL_OTP", owner.getEmail(),
                    "Verify your " + properties.getBrand().getProduct() + " shop",
                    "Your verification code is " + code + ". It expires in 10 minutes.");
        } catch (RuntimeException ignored) {
            /* shop is created; owner can request another code from login */
        }
        return issueTokens(owner, principal, client);
    }

    @Transactional
    public AuthResponse switchWorkspace(UUID userId, UUID workspaceId, ClientInfo client) {
        User user = userRepository.findWithRolesById(userId)
                .orElseThrow(() -> ApiException.notFound("User", userId));
        UserPrincipal principal = workspaceAccessService.select(userId, workspaceId);
        return issueTokens(user, principal, client);
    }

    /**
     * Rotates the refresh token on every use. Presenting an already-rotated
     * token means it leaked, so every session for that user is dropped.
     */
    @Transactional
    public AuthResponse refresh(RefreshRequest request, ClientInfo client) {
        String hash = jwtService.hashRefreshToken(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID, "Refresh token is not valid."));

        if (stored.getRevokedAt() != null) {
            log.warn("Reuse of a revoked refresh token for user {}; revoking all sessions",
                    stored.getUserId());
            refreshTokenRepository.revokeAllForUser(stored.getUserId(), Instant.now());
            throw new ApiException(ErrorCode.TOKEN_INVALID, "Session expired. Please sign in again.");
        }
        if (!stored.isActive()) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED, "Session expired. Please sign in again.");
        }

        User user = userRepository.findWithRolesById(stored.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID, "Account no longer exists."));
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED, "This account has been deactivated.");
        }

        UserPrincipal principal = workspaceAccessService.principalFor(user, user.getShopId());
        AuthResponse response = issueTokens(user, principal, client);

        stored.setRevokedAt(Instant.now());
        refreshTokenRepository.save(stored);

        return response;
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(jwtService.hashRefreshToken(refreshToken))
                .ifPresent(token -> {
                    token.setRevokedAt(Instant.now());
                    refreshTokenRepository.save(token);
                });
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findWithRolesByEmail(request.email()).ifPresent(user -> {
            String token = jwtService.generateRefreshToken();
            UserToken row = new UserToken();
            row.setUserId(user.getId());
            row.setEmail(user.getEmail());
            row.setTokenType(UserToken.PASSWORD_RESET);
            row.setTokenHash(jwtService.hashRefreshToken(token));
            row.setExpiresAt(Instant.now().plus(java.time.Duration.ofHours(2)));
            userTokenRepository.save(row);
            String url = properties.getAuth().getWebOrigin() + "/login?reset=" + token;
            try {
                emailDeliveryService.send(user.getShopId(), user.getId(), "PASSWORD_RESET", user.getEmail(),
                        "Reset your " + properties.getBrand().getProduct() + " password",
                        "Open this link to set a new password. It expires in 2 hours.\n\n" + url);
            } catch (RuntimeException ignored) {
                notificationService.emit(user.getShopId(), user.getId(), "PASSWORD_RESET", user.getEmail(),
                        "Reset your " + properties.getBrand().getProduct() + " password",
                        "A password reset was requested.");
            }
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        UserToken row = userTokenRepository
                .findByTokenTypeAndTokenHash(UserToken.PASSWORD_RESET, jwtService.hashRefreshToken(request.token()))
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID, "Reset link is not valid."));
        if (row.getUsedAt() != null || row.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED, "Reset link has expired.");
        }
        User user = userRepository.findById(row.getUserId()).orElseThrow();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
        row.setUsedAt(Instant.now());
        userTokenRepository.save(row);
        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
    }

    @Transactional
    public void requestOtp(OtpRequest request) {
        String phone = otpDeliveryService.normalizePhone(request.phone());
        var user = userRepository.findFirstByPhone(phone)
                .or(() -> userRepository.findFirstByPhone(request.phone()));
        var delivery = otpDeliveryService.sendPhone(phone, false, user.map(User::getId).orElse(null));
        UserToken row = new UserToken();
        row.setPhone(phone);
        row.setTokenType(UserToken.PHONE_OTP);
        row.setTokenHash(jwtService.hashRefreshToken(delivery.code()));
        row.setExpiresAt(Instant.now().plus(java.time.Duration.ofMinutes(10)));
        user.ifPresent(found -> row.setUserId(found.getId()));
        userTokenRepository.save(row);
    }

    @Transactional
    public void verifyOtp(VerifyOtpRequest request) {
        UserToken row = userTokenRepository
                .findByTokenTypeAndTokenHash(UserToken.PHONE_OTP, jwtService.hashRefreshToken(request.code()))
                .orElseThrow(() -> new ApiException(ErrorCode.OTP_INVALID, "That code is not valid."));
        if (row.getUsedAt() != null || row.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(ErrorCode.OTP_INVALID, "That code has expired.");
        }
        if (row.getPhone() != null && !row.getPhone().equals(request.phone())) {
            throw new ApiException(ErrorCode.OTP_INVALID, "That code is not valid.");
        }
        row.setUsedAt(Instant.now());
        userTokenRepository.save(row);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User", userId));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Current password is incorrect.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        // Changing a password ends every other session.
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());

        auditService.record(AuditAction.USER_PASSWORD_CHANGED, "User", userId,
                "%s changed their password".formatted(user.getFullName()));
    }

    @Transactional(readOnly = true)
    public List<DeviceSessionService.SessionCard> sessions(UUID userId, String currentDeviceId) {
        return deviceSessionService.listMine(userId, currentDeviceId);
    }

    @Transactional
    public void revokeSession(UUID userId, UUID sessionId) {
        deviceSessionService.revoke(userId, sessionId);
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser currentUser(UUID userId) {
        User user = userRepository.findWithRolesById(userId)
                .orElseThrow(() -> ApiException.notFound("User", userId));
        UUID selected = com.fixflow.security.CurrentUser.find()
                .map(UserPrincipal::getShopId)
                .orElse(user.getShopId());
        UserPrincipal principal = workspaceAccessService.principalFor(user, selected);
        return toAuthenticatedUser(user, principal);
    }

    // -----------------------------------------------------------------

    @Transactional
    public AuthResponse sessionFor(User user, ClientInfo client) {
        UserPrincipal principal = workspaceAccessService.principalFor(user, user.getShopId());
        return issueTokens(user, principal, client);
    }

    private AuthResponse issueTokens(User user, UserPrincipal principal, ClientInfo client) {
        String deviceId = deviceSessionService.register(user.getId(), principal.getShopId(), client);
        String accessToken = jwtService.createAccessToken(principal);

        String rawRefresh = jwtService.generateRefreshToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setTokenHash(jwtService.hashRefreshToken(rawRefresh));
        refreshToken.setDeviceId(deviceId);
        refreshToken.setUserAgent(truncate(client == null ? null : client.userAgent(), 400));
        refreshToken.setIpAddress(client == null ? null : client.ipAddress());
        refreshToken.setExpiresAt(jwtService.refreshTokenExpiry());
        refreshTokenRepository.save(refreshToken);

        List<WorkspaceCard> workspaces = workspaceAccessService.listMine(user.getId(), principal.getShopId())
                .workspaces();
        return AuthResponse.of(accessToken, rawRefresh, jwtService.accessTokenTtlSeconds(),
                toAuthenticatedUser(user, principal), workspaces, deviceId);
    }

    private void registerFailedLogin(User user) {
        int attempts = user.getFailedLogins() + 1;
        user.setFailedLogins(attempts);
        if (attempts >= properties.getSecurity().getMaxFailedLogins()) {
            user.setLockedUntil(Instant.now().plus(properties.getSecurity().getLockoutDuration()));
            log.warn("Locking account {} after {} failed attempts", user.getEmail(), attempts);
        }
        userRepository.save(user);
    }

    private AuthenticatedUser toAuthenticatedUser(User user, UserPrincipal principal) {
        Shop shop = principal.getShopId() == null ? null
                : shopRepository.findById(principal.getShopId()).orElse(null);
        Set<String> permissions = principal.getPermissions().stream()
                .map(Permission::name)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        UUID workspaceId = shop == null ? null : shop.getId();
        String workspaceName = shop == null ? null : shop.getName();
        return new AuthenticatedUser(user.getId(), workspaceId, workspaceName, workspaceId, workspaceName,
                user.getFullName(), user.getEmail(), user.getPhone(), user.getAvatarUrl(),
                principal.getRoles(), permissions, user.isMustChangePassword(), user.isSystemAdmin(),
                user.isEmailVerified(), user.isPhoneVerified(),
                workspaceId != null && !user.isSystemAdmin() && billingService.paymentRequired(workspaceId));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
