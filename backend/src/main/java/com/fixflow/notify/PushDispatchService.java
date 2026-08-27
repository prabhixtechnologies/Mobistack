package com.fixflow.notify;

import com.fixflow.config.FixFlowProperties;
import com.fixflow.notify.domain.PushDevice;
import com.fixflow.notify.repository.PushDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends alerts to phones through Expo's push service.
 *
 * <p>Expo answers with one ticket per message, and reading those tickets is the
 * only way to learn that a token is dead. Without that, a phone that has been
 * wiped or reinstalled is retried on every alert for ever, and the shop is
 * never told that its notifications stopped arriving.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushDispatchService {

    private static final String EXPO_SEND = "https://exp.host/--/api/v2/push/send";

    /** Expo's word for "this token will never work again". */
    private static final String DEAD_TOKEN = "DeviceNotRegistered";

    private static final ParameterizedTypeReference<Map<String, Object>> EXPO_REPLY =
            new ParameterizedTypeReference<>() {
            };

    private final PushDeviceRepository pushDeviceRepository;
    private final FixFlowProperties properties;

    /** The result of one alert, so the outbox can record what really happened. */
    public record Delivery(int accepted, int rejected, String detail) {
        public String status() {
            if (accepted > 0) {
                return "SENT";
            }
            return rejected > 0 ? "FAILED" : "SKIPPED";
        }

        static Delivery skipped(String detail) {
            return new Delivery(0, 0, detail);
        }
    }

    public PushDevice register(UUID userId, UUID shopId, String deviceId, String platform,
                               String expoPushToken, String appVersion, Integer nativeBuild) {
        PushDevice row = pushDeviceRepository.findByUserIdAndDeviceId(userId, deviceId)
                .orElseGet(PushDevice::new);
        row.setUserId(userId);
        row.setShopId(shopId);
        row.setDeviceId(deviceId);
        row.setPlatform(platform == null || platform.isBlank() ? "WEB" : platform.trim().toUpperCase());
        if (expoPushToken != null && !expoPushToken.isBlank()) {
            String token = expoPushToken.trim();
            // A re-register is how a phone recovers from a retired token, so
            // clear the failure state whenever a fresh token arrives.
            if (!token.equals(row.getExpoPushToken())) {
                row.setRetiredAt(null);
                row.setLastError(null);
            }
            row.setExpoPushToken(token);
        }
        row.setAppVersion(appVersion);
        row.setNativeBuild(nativeBuild);
        row.setLastSeenAt(Instant.now());
        return pushDeviceRepository.save(row);
    }

    /**
     * Delivers one alert to every live device belonging to a person.
     *
     * <p>Deliberately not transactional: this makes an outbound HTTP call, and
     * holding a database connection open across it is what exhausts the pool
     * when Expo is slow.
     */
    public Delivery sendToUser(UUID userId, String title, String body, String link) {
        List<PushDevice> devices =
                pushDeviceRepository.findByUserIdAndExpoPushTokenIsNotNullAndRetiredAtIsNull(userId);
        if (devices.isEmpty()) {
            return Delivery.skipped("No phone is registered for alerts.");
        }
        List<Map<String, Object>> messages = devices.stream().map(device -> {
            Map<String, Object> message = new HashMap<>();
            message.put("to", device.getExpoPushToken());
            message.put("title", title);
            message.put("body", body);
            message.put("sound", "default");
            message.put("channelId", "mobistack-default");
            message.put("data", link == null ? Map.of() : Map.of("link", link));
            return message;
        }).toList();

        Map<String, Object> response;
        try {
            response = RestClient.create()
                    .post()
                    .uri(EXPO_SEND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Accept", "application/json")
                    .headers(headers -> {
                        String accessToken = properties.getPush().getExpoAccessToken();
                        if (accessToken != null && !accessToken.isBlank()) {
                            headers.setBearerAuth(accessToken);
                        }
                    })
                    .body(messages)
                    .retrieve()
                    .body(EXPO_REPLY);
        } catch (Exception ex) {
            log.warn("Expo push call failed for user {}: {}", userId, ex.getMessage());
            return new Delivery(0, devices.size(), "Could not reach the notification service.");
        }
        return recordTickets(devices, response);
    }

    /**
     * Matches Expo's tickets back to the devices they were sent for. Tickets
     * come back in request order, which is the only correlation Expo offers.
     *
     * <p>Package-private rather than private so the ticket handling can be tested
     * without standing up an HTTP server; it is a pure function of its inputs.
     */
    Delivery recordTickets(List<PushDevice> devices, Map<String, Object> response) {
        List<?> tickets = response == null ? List.of() : asList(response.get("data"));
        if (tickets.isEmpty()) {
            return new Delivery(devices.size(), 0, null);
        }
        int accepted = 0;
        int rejected = 0;
        String firstProblem = null;
        List<PushDevice> changed = new ArrayList<>();
        for (int i = 0; i < devices.size() && i < tickets.size(); i++) {
            PushDevice device = devices.get(i);
            Map<?, ?> ticket = tickets.get(i) instanceof Map<?, ?> map ? map : Map.of();
            if ("ok".equals(ticket.get("status"))) {
                accepted++;
                if (device.getLastError() != null) {
                    device.setLastError(null);
                    changed.add(device);
                }
                continue;
            }
            rejected++;
            Object reported = ticket.get("message");
            String message = reported == null ? "Rejected by the notification service" : String.valueOf(reported);
            if (firstProblem == null) {
                firstProblem = message;
            }
            device.setLastError(truncate(message, 200));
            if (DEAD_TOKEN.equals(errorCode(ticket))) {
                device.setRetiredAt(Instant.now());
                log.info("Retiring push token for user {} device {}: {}",
                        device.getUserId(), device.getDeviceId(), message);
            }
            changed.add(device);
        }
        if (!changed.isEmpty()) {
            pushDeviceRepository.saveAll(changed);
        }
        return new Delivery(accepted, rejected, firstProblem);
    }

    private static String errorCode(Map<?, ?> ticket) {
        return ticket.get("details") instanceof Map<?, ?> details
                ? String.valueOf(details.get("error"))
                : null;
    }

    private static List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
