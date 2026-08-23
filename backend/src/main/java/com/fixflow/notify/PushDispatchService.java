package com.fixflow.notify;

import com.fixflow.notify.domain.PushDevice;
import com.fixflow.notify.repository.PushDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushDispatchService {

    private final PushDeviceRepository pushDeviceRepository;

    public PushDevice register(UUID userId, UUID shopId, String deviceId, String platform,
                               String expoPushToken, String appVersion, Integer nativeBuild) {
        PushDevice row = pushDeviceRepository.findByUserIdAndDeviceId(userId, deviceId)
                .orElseGet(PushDevice::new);
        row.setUserId(userId);
        row.setShopId(shopId);
        row.setDeviceId(deviceId);
        row.setPlatform(platform == null || platform.isBlank() ? "WEB" : platform.trim().toUpperCase());
        if (expoPushToken != null && !expoPushToken.isBlank()) {
            row.setExpoPushToken(expoPushToken.trim());
        }
        row.setAppVersion(appVersion);
        row.setNativeBuild(nativeBuild);
        row.setLastSeenAt(Instant.now());
        return pushDeviceRepository.save(row);
    }

    public void sendToUser(UUID userId, String title, String body, String link) {
        List<PushDevice> devices = pushDeviceRepository.findByUserIdAndExpoPushTokenIsNotNull(userId);
        if (devices.isEmpty()) {
            return;
        }
        List<Map<String, Object>> messages = devices.stream().map(device -> {
            Map<String, Object> message = new HashMap<>();
            message.put("to", device.getExpoPushToken());
            message.put("title", title);
            message.put("body", body);
            message.put("sound", "default");
            if (link != null) {
                message.put("data", Map.of("link", link));
            }
            return message;
        }).toList();
        try {
            RestClient.create()
                    .post()
                    .uri("https://exp.host/--/api/v2/push/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Accept", "application/json")
                    .header("Accept-Encoding", "gzip, deflate")
                    .body(messages)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Expo push failed for user {}: {}", userId, ex.getMessage());
        }
    }
}
