package com.fixflow.notify.web;

import com.fixflow.notify.PushDispatchService;
import com.fixflow.notify.domain.InboxNotification;
import com.fixflow.notify.domain.PushDevice;
import com.fixflow.notify.repository.InboxNotificationRepository;
import com.fixflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inbox")
@RequiredArgsConstructor
@Tag(name = "Inbox")
public class InboxController {

    public record InboxCard(UUID id, String eventType, String title, String body, String link, Instant createdAt,
                            Instant readAt) {
    }

    public record InboxSummary(long unread, List<InboxCard> items) {
    }

    public record PushTokenRequest(String deviceId, String platform, String expoPushToken, String appVersion,
                                   Integer nativeBuild) {
    }

    private final InboxNotificationRepository inboxNotificationRepository;
    private final PushDispatchService pushDispatchService;

    @GetMapping
    public InboxSummary list() {
        UUID userId = CurrentUser.userId();
        List<InboxCard> items = inboxNotificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 50))
                .getContent()
                .stream()
                .map(InboxController::toCard)
                .toList();
        return new InboxSummary(inboxNotificationRepository.countByUserIdAndReadAtIsNull(userId), items);
    }

    @PostMapping("/{id}/read")
    public InboxCard read(@PathVariable UUID id) {
        InboxNotification row = inboxNotificationRepository.findById(id)
                .orElseThrow(() -> com.fixflow.common.error.ApiException.notFound("Notification", id));
        if (!row.getUserId().equals(CurrentUser.userId())) {
            throw com.fixflow.common.error.ApiException.forbidden("That notification is not yours.");
        }
        row.setReadAt(Instant.now());
        return toCard(inboxNotificationRepository.save(row));
    }

    @PostMapping("/read-all")
    @Transactional
    public InboxSummary readAll() {
        inboxNotificationRepository.markAllRead(CurrentUser.userId(), Instant.now());
        return list();
    }

    @PostMapping("/push-token")
    public PushDevice registerPush(@RequestBody PushTokenRequest request) {
        return pushDispatchService.register(CurrentUser.userId(),
                CurrentUser.find().map(principal -> principal.getShopId()).orElse(null),
                request.deviceId(), request.platform(), request.expoPushToken(), request.appVersion(),
                request.nativeBuild());
    }

    private static InboxCard toCard(InboxNotification row) {
        return new InboxCard(row.getId(), row.getEventType(), row.getTitle(), row.getBody(), row.getLink(),
                row.getCreatedAt(), row.getReadAt());
    }
}
