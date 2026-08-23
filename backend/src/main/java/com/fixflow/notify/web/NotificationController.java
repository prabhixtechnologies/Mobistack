package com.fixflow.notify.web;

import com.fixflow.notify.NotificationService;
import com.fixflow.notify.domain.NotificationOutbox;
import com.fixflow.notify.domain.NotificationPreference;
import com.fixflow.notify.repository.NotificationOutboxRepository;
import com.fixflow.notify.repository.NotificationPreferenceRepository;
import com.fixflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationService notificationService;

    public record PreferenceRequest(String eventType, boolean email, boolean whatsapp, boolean push, boolean sms) {
    }

    @GetMapping("/preferences")
    public List<NotificationPreference> preferences() {
        List<NotificationPreference> existing = preferenceRepository.findByUserIdOrderByEventTypeAsc(CurrentUser.userId());
        if (!existing.isEmpty()) {
            return existing;
        }
        return NotificationService.DEFAULT_EVENTS.stream()
                .map(event -> notificationService.defaultPreference(CurrentUser.userId(), event))
                .toList();
    }

    @PutMapping("/preferences")
    public List<NotificationPreference> save(@RequestBody List<PreferenceRequest> body) {
        return body.stream().map(request -> {
            NotificationPreference row = preferenceRepository
                    .findByUserIdAndEventType(CurrentUser.userId(), request.eventType())
                    .orElseGet(NotificationPreference::new);
            row.setUserId(CurrentUser.userId());
            row.setEventType(request.eventType());
            row.setEmail(request.email());
            row.setWhatsapp(request.whatsapp());
            row.setPush(request.push());
            row.setSms(request.sms());
            return preferenceRepository.save(row);
        }).toList();
    }

    @GetMapping
    public List<NotificationOutbox> recent() {
        return outboxRepository.findByShopIdOrderByCreatedAtDesc(CurrentUser.shopId(), PageRequest.of(0, 40))
                .getContent();
    }
}
