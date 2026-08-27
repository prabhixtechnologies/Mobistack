package com.fixflow.auth.service;

import com.fixflow.audit.service.AuditService;
import com.fixflow.auth.domain.RefreshToken;
import com.fixflow.auth.dto.AuthDtos.RefreshRequest;
import com.fixflow.auth.repository.RefreshTokenRepository;
import com.fixflow.auth.repository.UserTokenRepository;
import com.fixflow.billing.service.BillingService;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import com.fixflow.notify.EmailDeliveryService;
import com.fixflow.notify.NotificationService;
import com.fixflow.notify.OtpDeliveryService;
import com.fixflow.security.UserPrincipal;
import com.fixflow.security.jwt.JwtService;
import com.fixflow.shop.repository.ShopRepository;
import com.fixflow.shop.service.ShopProvisioningService;
import com.fixflow.user.domain.User;
import com.fixflow.user.repository.UserRepository;
import com.fixflow.workspace.dto.WorkspaceDtos.MyWorkspacesResponse;
import com.fixflow.workspace.service.WorkspaceAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Refresh tokens rotate on use, so a client with several requests in flight can
 * legitimately present the same token twice. Treating that as theft — and
 * responding by revoking every session — is what signed users out at random.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshTest {

    @Mock private UserRepository userRepository;
    @Mock private ShopRepository shopRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private ShopProvisioningService shopProvisioningService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuditService auditService;
    @Mock private FixFlowProperties properties;
    @Mock private WorkspaceAccessService workspaceAccessService;
    @Mock private UserTokenRepository userTokenRepository;
    @Mock private NotificationService notificationService;
    @Mock private OtpDeliveryService otpDeliveryService;
    @Mock private DeviceSessionService deviceSessionService;
    @Mock private BillingService billingService;
    @Mock private EmailDeliveryService emailDeliveryService;

    @InjectMocks
    private AuthService authService;

    private User user;
    private AuthService.ClientInfo client;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("owner@example.com");
        user.setFullName("Shop Owner");
        user.setActive(true);
        client = new AuthService.ClientInfo("web-1", "MobiStack", "10.0.0.2");

        lenient().when(jwtService.hashRefreshToken(anyString()))
                .thenAnswer(invocation -> "hash:" + invocation.getArgument(0));
    }

    /** Stubs just enough for a rotation to succeed. */
    private void allowIssuing() {
        when(userRepository.findWithRolesById(user.getId())).thenReturn(Optional.of(user));
        when(workspaceAccessService.principalFor(eq(user), any()))
                .thenReturn(UserPrincipal.unscoped(user));
        when(deviceSessionService.register(eq(user.getId()), any(), any(), eq(false))).thenReturn("web-1");
        when(jwtService.createAccessToken(any(UserPrincipal.class), eq("web-1"))).thenReturn("access-1");
        when(jwtService.generateRefreshToken()).thenReturn("rotated-raw");
        when(jwtService.refreshTokenExpiry()).thenReturn(Instant.now().plusSeconds(2_592_000));
        when(jwtService.accessTokenTtlSeconds()).thenReturn(1800L);
        when(workspaceAccessService.listMine(eq(user.getId()), any()))
                .thenReturn(new MyWorkspacesResponse(null, List.of()));
        when(billingService.features(any())).thenReturn(java.util.Set.of());
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
    }

    private RefreshToken storedToken() {
        RefreshToken stored = new RefreshToken();
        stored.setId(UUID.randomUUID());
        stored.setUserId(user.getId());
        stored.setDeviceId("web-1");
        stored.setTokenHash("hash:live-token");
        stored.setExpiresAt(Instant.now().plusSeconds(3600));
        return stored;
    }

    @Test
    void rotatesAFreshTokenAndRecordsItsSuccessor() {
        RefreshToken stored = storedToken();
        when(refreshTokenRepository.findByTokenHash("hash:live-token")).thenReturn(Optional.of(stored));
        allowIssuing();

        var response = authService.refresh(new RefreshRequest("live-token", "web-1"), client);

        assertThat(response.accessToken()).isEqualTo("access-1");
        assertThat(response.refreshToken()).isEqualTo("rotated-raw");
        assertThat(stored.getRevokedAt()).isNotNull();
        assertThat(stored.getReplacedBy()).isNotNull();
    }

    @Test
    void honoursASecondParallelRefreshInsteadOfEndingTheSession() {
        // What a browser does when several requests expire together: the first
        // call rotated this token a moment ago, and the second arrives holding it.
        RefreshToken stored = storedToken();
        stored.setRevokedAt(Instant.now().minusSeconds(2));
        stored.setReplacedBy(UUID.randomUUID());
        when(refreshTokenRepository.findByTokenHash("hash:live-token")).thenReturn(Optional.of(stored));
        allowIssuing();

        var response = authService.refresh(new RefreshRequest("live-token", "web-1"), client);

        assertThat(response.accessToken()).isEqualTo("access-1");
        verify(refreshTokenRepository, never()).revokeAllForUser(any(), any());
        verify(refreshTokenRepository, never()).revokeByUserAndDevice(any(), any(), any());
    }

    @Test
    void treatsAReplayLongAfterRotationAsALeakButOnlyDropsThatDevice() {
        RefreshToken stored = storedToken();
        stored.setRevokedAt(Instant.now().minusSeconds(600));
        stored.setReplacedBy(UUID.randomUUID());
        when(refreshTokenRepository.findByTokenHash("hash:live-token")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("live-token", "web-1"), client))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.TOKEN_INVALID);

        verify(refreshTokenRepository).revokeByUserAndDevice(eq(user.getId()), eq("web-1"), any());
        verify(refreshTokenRepository, never()).revokeAllForUser(any(), any());
    }

    @Test
    void aTokenRevokedWithoutRotationIsNotHonoured() {
        // Sign-out and password changes revoke without a successor.
        RefreshToken stored = storedToken();
        stored.setRevokedAt(Instant.now().minusSeconds(2));
        when(refreshTokenRepository.findByTokenHash("hash:live-token")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("live-token", "web-1"), client))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    void anExpiredTokenAsksTheUserToSignInAgain() {
        RefreshToken stored = storedToken();
        stored.setExpiresAt(Instant.now().minusSeconds(60));
        when(refreshTokenRepository.findByTokenHash("hash:live-token")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("live-token", "web-1"), client))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);

        verify(refreshTokenRepository, never()).revokeAllForUser(any(), any());
    }

    @Test
    void anUnknownTokenIsRejectedWithoutTouchingOtherSessions() {
        when(refreshTokenRepository.findByTokenHash("hash:bogus")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("bogus", "web-1"), client))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.TOKEN_INVALID);

        verify(refreshTokenRepository, never()).revokeAllForUser(any(), any());
    }

    @Test
    void aDeactivatedAccountCannotRefresh() {
        RefreshToken stored = storedToken();
        when(refreshTokenRepository.findByTokenHash("hash:live-token")).thenReturn(Optional.of(stored));
        user.setActive(false);
        when(userRepository.findWithRolesById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("live-token", "web-1"), client))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.ACCOUNT_DISABLED);
    }
}
