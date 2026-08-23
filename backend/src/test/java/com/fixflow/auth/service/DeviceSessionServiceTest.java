package com.fixflow.auth.service;

import com.fixflow.auth.domain.RefreshToken;
import com.fixflow.auth.repository.RefreshTokenRepository;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import com.fixflow.shop.domain.Shop;
import com.fixflow.shop.repository.ShopRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceSessionServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private ShopRepository shopRepository;

    private DeviceSessionService service;
    private FixFlowProperties properties;
    private UUID userId;
    private UUID shopId;

    @BeforeEach
    void setUp() {
        properties = new FixFlowProperties();
        properties.getDevices().setDefaultMaxPerUser(2);
        properties.getDevices().setOverLimit("revoke-oldest");
        service = new DeviceSessionService(refreshTokenRepository, shopRepository, properties);
        userId = UUID.randomUUID();
        shopId = UUID.randomUUID();
    }

    @Test
    void reusesTheClientDeviceId() {
        when(refreshTokenRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(eq(userId), any()))
                .thenReturn(List.of());
        String deviceId = service.register(userId, shopId, new AuthService.ClientInfo("tablet-1", "FixFlow", "10.0.0.4"));
        assertThat(deviceId).isEqualTo("tablet-1");
        verify(refreshTokenRepository).revokeByUserAndDevice(eq(userId), eq("tablet-1"), any());
    }

    @Test
    void rejectsAFourthDeviceWhenPolicyIsReject() {
        properties.getDevices().setOverLimit("reject");
        Shop shop = new Shop();
        shop.setMaxDevicesPerUser(2);
        when(shopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(refreshTokenRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(eq(userId), any()))
                .thenReturn(List.of(token("phone-a"), token("phone-b")));

        assertThatThrownBy(() -> service.register(userId, shopId,
                new AuthService.ClientInfo("phone-c", "FixFlow", "10.0.0.8")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.DEVICE_LIMIT_REACHED);
    }

    private RefreshToken token(String deviceId) {
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setDeviceId(deviceId);
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        return token;
    }
}
