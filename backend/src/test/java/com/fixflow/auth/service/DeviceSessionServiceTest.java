package com.fixflow.auth.service;

import com.fixflow.auth.domain.RefreshToken;
import com.fixflow.auth.repository.RefreshTokenRepository;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import com.fixflow.shop.domain.Shop;
import com.fixflow.shop.repository.ShopRepository;
import com.fixflow.workspace.domain.MembershipStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A screen seat is a person; a device is one of that person's screens. These
 * tests pin that distinction down, because collapsing the two is what signed
 * owners out of the web console every time they used the counter phone.
 */
@ExtendWith(MockitoExtension.class)
class DeviceSessionServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private ShopRepository shopRepository;
    @Mock
    private com.fixflow.notify.WorkspaceNotifier notifier;

    private FixFlowProperties properties;
    private DeviceSessionService service;
    private UUID userId;
    private UUID shopId;

    @BeforeEach
    void setUp() {
        properties = new FixFlowProperties();
        service = new DeviceSessionService(refreshTokenRepository, shopRepository, properties, notifier);
        userId = UUID.randomUUID();
        shopId = UUID.randomUUID();
    }

    private RefreshToken token(String deviceId) {
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setDeviceId(deviceId);
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        return token;
    }

    private void liveDevices(String... deviceIds) {
        when(refreshTokenRepository
                .findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(eq(userId), any()))
                .thenReturn(java.util.Arrays.stream(deviceIds).map(this::token).toList());
    }

    private void shopWithDeviceCap(int cap) {
        Shop shop = new Shop();
        shop.setMaxDevicesPerUser(cap);
        when(shopRepository.findById(shopId)).thenReturn(Optional.of(shop));
    }

    @Test
    void keepsTheClientSuppliedDeviceIdAndReplacesOnlyThatDevicesChain() {
        liveDevices("web-1", "phone-2");

        String deviceId = service.register(userId, shopId,
                new AuthService.ClientInfo("web-1", "MobiStack", "10.0.0.4"), false);

        assertThat(deviceId).isEqualTo("web-1");
        verify(refreshTokenRepository).revokeByUserAndDevice(eq(userId), eq("web-1"), any());
        verify(refreshTokenRepository, never()).revokeByUserAndDevice(eq(userId), eq("phone-2"), any());
        verify(refreshTokenRepository, never()).revokeAllForUser(eq(userId), any());
    }

    @Test
    void signingInOnAPhoneDoesNotEndTheSamePersonsWebSession() {
        liveDevices("web-1");
        shopWithDeviceCap(3);

        service.register(userId, shopId, new AuthService.ClientInfo("phone-2", "MobiStack", "10.0.0.5"), false);

        verify(refreshTokenRepository).revokeByUserAndDevice(eq(userId), eq("phone-2"), any());
        verify(refreshTokenRepository, never()).revokeByUserAndDevice(eq(userId), eq("web-1"), any());
        verify(refreshTokenRepository, never()).revokeAllForUser(eq(userId), any());
    }

    @Test
    void aSecondDeviceForTheSamePersonDoesNotConsumeASecondSeat() {
        liveDevices("web-1");
        shopWithDeviceCap(3);

        service.register(userId, shopId, new AuthService.ClientInfo("phone-2", "MobiStack", "10.0.0.5"), false);

        verify(refreshTokenRepository, never()).countOtherSeatedUsers(any(), any(), any(), any());
    }

    @Test
    void rejectsANewPersonWhenEveryPaidScreenIsTaken() {
        Shop shop = new Shop();
        shop.setExtraScreens(0);
        when(shopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        liveDevices();
        when(refreshTokenRepository.countOtherSeatedUsers(eq(shopId), eq(userId), eq(MembershipStatus.ACTIVE), any()))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.register(userId, shopId,
                new AuthService.ClientInfo("phone-c", "MobiStack", "10.0.0.8"), false))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.DEVICE_LIMIT_REACHED);
        verify(refreshTokenRepository, never()).revokeAllForUser(eq(userId), any());
    }

    @Test
    void endsTheLeastRecentlyUsedDeviceOnceThePerUserCapIsFull() {
        liveDevices("oldest-1", "middle-2", "newest-3");
        shopWithDeviceCap(3);

        service.register(userId, shopId, new AuthService.ClientInfo("fourth-4", "MobiStack", "10.0.0.6"), false);

        verify(refreshTokenRepository).revokeByUserAndDevice(eq(userId), eq("oldest-1"), any());
        verify(refreshTokenRepository).revokeByUserAndDevice(eq(userId), eq("fourth-4"), any());
        verify(refreshTokenRepository, never()).revokeByUserAndDevice(eq(userId), eq("middle-2"), any());
        verify(refreshTokenRepository, never()).revokeAllForUser(eq(userId), any());
    }

    @Test
    void refusesTheExtraDeviceWhenConfiguredToRejectRatherThanEvict() {
        properties.getDevices().setOverLimit("reject");
        liveDevices("web-1", "phone-2", "tablet-3");
        shopWithDeviceCap(3);

        assertThatThrownBy(() -> service.register(userId, shopId,
                new AuthService.ClientInfo("fourth-4", "MobiStack", "10.0.0.7"), false))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.DEVICE_LIMIT_REACHED);
    }

    @Test
    void aShopSettingCannotRaiseTheCapAboveTheConfiguredCeiling() {
        properties.getDevices().setAbsoluteMaxPerUser(2);
        liveDevices("web-1", "phone-2");
        shopWithDeviceCap(50);

        service.register(userId, shopId, new AuthService.ClientInfo("third-3", "MobiStack", "10.0.0.9"), false);

        verify(refreshTokenRepository).revokeByUserAndDevice(eq(userId), eq("web-1"), any());
    }

    @Test
    void livenessIsReadFromTheDatabaseSoEveryInstanceAgrees() {
        when(refreshTokenRepository.existsByUserIdAndDeviceIdAndRevokedAtIsNullAndExpiresAtAfter(
                eq(userId), eq("web-1"), any())).thenReturn(true);

        assertThat(service.isLive(userId, "web-1")).isTrue();
    }

    @Test
    void aRevokedDeviceIsNoLongerLive() {
        when(refreshTokenRepository.existsByUserIdAndDeviceIdAndRevokedAtIsNullAndExpiresAtAfter(
                eq(userId), eq("evicted-9"), any())).thenReturn(false);

        assertThat(service.isLive(userId, "evicted-9")).isFalse();
    }

    @Test
    void lapsedExtraScreensDoNotCountAsSeats() {
        Shop shop = new Shop();
        shop.setExtraScreens(2);
        shop.setExtraScreensPeriodEnd(Instant.now().minusSeconds(90));
        when(shopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(refreshTokenRepository.countSeatedUsers(eq(shopId), eq(MembershipStatus.ACTIVE), any()))
                .thenReturn(1L);

        var capacity = service.capacity(shopId);

        assertThat(capacity.subscribed()).isEqualTo(2);
        assertThat(capacity.extra()).isEqualTo(0);
        assertThat(capacity.seats()).isEqualTo(1);
        assertThat(capacity.live()).isFalse();
    }

    @Test
    void platformStaffBypassBothTheSeatCapAndTheDeviceCap() {
        liveDevices("admin-1", "admin-2", "admin-3", "admin-4");

        String deviceId = service.register(userId, shopId,
                new AuthService.ClientInfo("admin-laptop", "MobiStack", "10.0.0.9"), true);

        assertThat(deviceId).isEqualTo("admin-laptop");
        verify(refreshTokenRepository).revokeByUserAndDevice(eq(userId), eq("admin-laptop"), any());
        verify(refreshTokenRepository, never()).revokeAllForUser(eq(userId), any());
    }

    @Test
    void fallsBackToAStableFingerprintWhenTheClientSendsNoDeviceId() {
        AuthService.ClientInfo client = new AuthService.ClientInfo(null, "MobiStack", "10.0.0.3");

        String first = service.resolveDeviceId(client);
        String second = service.resolveDeviceId(client);

        assertThat(first).startsWith("anon-").isEqualTo(second);
    }

    @Test
    void listedSessionsMarkTheCallersOwnDevice() {
        RefreshToken web = token("web-1");
        RefreshToken phone = token("phone-2");
        when(refreshTokenRepository
                .findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(eq(userId), any()))
                .thenReturn(List.of(web, phone));

        var sessions = service.listMine(userId, "phone-2");

        assertThat(sessions).hasSize(2);
        assertThat(sessions).filteredOn(DeviceSessionService.SessionCard::current)
                .extracting(DeviceSessionService.SessionCard::deviceId)
                .containsExactly("phone-2");
    }
}
