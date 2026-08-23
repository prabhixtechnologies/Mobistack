package com.fixflow.auth.service;

import com.fixflow.auth.repository.RefreshTokenRepository;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.shop.domain.Shop;
import com.fixflow.shop.repository.ShopRepository;
import com.fixflow.workspace.domain.MembershipStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceSessionServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private ShopRepository shopRepository;

    private DeviceSessionService service;
    private UUID userId;
    private UUID shopId;

    @BeforeEach
    void setUp() {
        service = new DeviceSessionService(refreshTokenRepository, shopRepository);
        userId = UUID.randomUUID();
        shopId = UUID.randomUUID();
    }

    @Test
    void reusesTheClientDeviceIdAndEndsEveryOtherSession() {
        when(refreshTokenRepository.existsByUserIdAndRevokedAtIsNullAndExpiresAtAfter(eq(userId), any()))
                .thenReturn(true);
        String deviceId = service.register(userId, shopId, new AuthService.ClientInfo("tablet-1", "MobiStack", "10.0.0.4"), false);
        assertThat(deviceId).isEqualTo("tablet-1");
        verify(refreshTokenRepository).revokeAllForUser(eq(userId), any());
        assertThat(service.isLive(userId, "tablet-1")).isTrue();
        assertThat(service.isLive(userId, "phone-2")).isFalse();
    }

    @Test
    void rejectsANewPersonWhenTheOnlyScreenIsTaken() {
        Shop shop = new Shop();
        shop.setExtraScreens(0);
        when(shopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(refreshTokenRepository.existsByUserIdAndRevokedAtIsNullAndExpiresAtAfter(eq(userId), any()))
                .thenReturn(false);
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
    void letsTheSameUserMoveToAnotherScreen() {
        when(refreshTokenRepository.existsByUserIdAndRevokedAtIsNullAndExpiresAtAfter(eq(userId), any()))
                .thenReturn(true);

        String deviceId = service.register(userId, shopId,
                new AuthService.ClientInfo("counter-2", "MobiStack", "10.0.0.2"), false);

        assertThat(deviceId).isEqualTo("counter-2");
        verify(refreshTokenRepository).revokeAllForUser(eq(userId), any());
        verify(refreshTokenRepository, never()).countOtherSeatedUsers(any(), any(), any(), any());
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
    void platformStaffBypassTheShopSeatCap() {
        when(refreshTokenRepository.existsByUserIdAndRevokedAtIsNullAndExpiresAtAfter(eq(userId), any()))
                .thenReturn(false);

        String deviceId = service.register(userId, shopId,
                new AuthService.ClientInfo("admin-laptop", "MobiStack", "10.0.0.9"), true);

        assertThat(deviceId).isEqualTo("admin-laptop");
        verify(refreshTokenRepository).revokeAllForUser(eq(userId), any());
    }
}
