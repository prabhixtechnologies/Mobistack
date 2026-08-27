package com.fixflow.notify;

import com.fixflow.config.FixFlowProperties;
import com.fixflow.notify.domain.PushDevice;
import com.fixflow.notify.repository.PushDeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Push used to be fire-and-forget, which meant a shop could go weeks without
 * alerts and nothing recorded that delivery had stopped. These tests cover the
 * parts that decide whether a token lives or is retired.
 */
@ExtendWith(MockitoExtension.class)
class PushDispatchServiceTest {

    @Mock
    private PushDeviceRepository pushDeviceRepository;

    private PushDispatchService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new PushDispatchService(pushDeviceRepository, new FixFlowProperties());
        userId = UUID.randomUUID();
    }

    @Test
    void aPersonWithNoRegisteredPhoneIsSkippedRatherThanFailed() {
        when(pushDeviceRepository.findByUserIdAndExpoPushTokenIsNotNullAndRetiredAtIsNull(userId))
                .thenReturn(List.of());

        PushDispatchService.Delivery delivery = service.sendToUser(userId, "Low stock", "Two left", null);

        assertThat(delivery.status()).isEqualTo("SKIPPED");
        assertThat(delivery.detail()).contains("No phone");
    }

    @Test
    void aFreshTokenClearsTheFailureRecordedAgainstTheDevice() {
        PushDevice device = device("ExponentPushToken[live]");
        device.setLastError("DeviceNotRegistered");
        device.setRetiredAt(Instant.now());
        when(pushDeviceRepository.findByUserIdAndDeviceId(userId, "counter-phone"))
                .thenReturn(Optional.of(device));
        when(pushDeviceRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        PushDevice saved = service.register(userId, UUID.randomUUID(), "counter-phone", "android",
                "ExponentPushToken[brand-new]", "1.2.0", 7);

        // Re-registering is exactly how a phone recovers, so the retirement lifts.
        assertThat(saved.getRetiredAt()).isNull();
        assertThat(saved.getLastError()).isNull();
        assertThat(saved.getExpoPushToken()).isEqualTo("ExponentPushToken[brand-new]");
    }

    @Test
    void reRegisteringTheSameTokenLeavesItsStateAlone() {
        PushDevice device = device("ExponentPushToken[same]");
        Instant retired = Instant.now().minusSeconds(60);
        device.setRetiredAt(retired);
        when(pushDeviceRepository.findByUserIdAndDeviceId(userId, "counter-phone"))
                .thenReturn(Optional.of(device));
        when(pushDeviceRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        PushDevice saved = service.register(userId, UUID.randomUUID(), "counter-phone", "android",
                "ExponentPushToken[same]", "1.2.0", 7);

        assertThat(saved.getRetiredAt()).isEqualTo(retired);
    }

    @Test
    void aDeliveryIsSentWhenEveryTicketIsAccepted() {
        List<PushDevice> devices = List.of(device("ExponentPushToken[a]"), device("ExponentPushToken[b]"));

        PushDispatchService.Delivery delivery = service.recordTickets(devices,
                Map.of("data", List.of(Map.of("status", "ok", "id", "t1"), Map.of("status", "ok", "id", "t2"))));

        assertThat(delivery.status()).isEqualTo("SENT");
        assertThat(delivery.accepted()).isEqualTo(2);
        assertThat(delivery.rejected()).isZero();
    }

    @Test
    void aDeadTokenIsRetiredSoItIsNotRetriedForEver() {
        PushDevice live = device("ExponentPushToken[live]");
        PushDevice dead = device("ExponentPushToken[dead]");

        PushDispatchService.Delivery delivery = service.recordTickets(List.of(live, dead), Map.of("data", List.of(
                Map.of("status", "ok", "id", "t1"),
                Map.of("status", "error", "message", "\"ExponentPushToken[dead]\" is not a registered push token",
                        "details", Map.of("error", "DeviceNotRegistered")))));

        assertThat(delivery.status()).isEqualTo("SENT");
        assertThat(delivery.accepted()).isEqualTo(1);
        assertThat(delivery.rejected()).isEqualTo(1);
        assertThat(live.getRetiredAt()).isNull();
        assertThat(dead.getRetiredAt()).isNotNull();
        assertThat(dead.getLastError()).contains("not a registered push token");
    }

    @Test
    void aTemporaryRejectionIsRecordedButTheTokenIsKept() {
        PushDevice device = device("ExponentPushToken[a]");

        PushDispatchService.Delivery delivery = service.recordTickets(List.of(device), Map.of("data", List.of(
                Map.of("status", "error", "message", "Too many requests",
                        "details", Map.of("error", "MessageRateExceeded")))));

        assertThat(delivery.status()).isEqualTo("FAILED");
        assertThat(device.getRetiredAt()).isNull();
        assertThat(device.getLastError()).isEqualTo("Too many requests");
    }

    @Test
    void aReplyWithNoTicketsIsTreatedAsAcceptedRatherThanLost() {
        PushDevice device = device("ExponentPushToken[a]");

        PushDispatchService.Delivery delivery = service.recordTickets(List.of(device), Map.of());

        assertThat(delivery.status()).isEqualTo("SENT");
        verify(pushDeviceRepository, never()).saveAll(any());
    }

    private PushDevice device(String token) {
        PushDevice device = new PushDevice();
        device.setUserId(userId);
        device.setDeviceId("device-" + token);
        device.setPlatform("ANDROID");
        device.setExpoPushToken(token);
        return device;
    }
}
