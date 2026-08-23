package com.fixflow.notify;

import com.fixflow.flags.service.FeatureFlagService;
import com.fixflow.notify.domain.InboxNotification;
import com.fixflow.notify.domain.NotificationOutbox;
import com.fixflow.notify.domain.NotificationPreference;
import com.fixflow.notify.repository.InboxNotificationRepository;
import com.fixflow.notify.repository.NotificationOutboxRepository;
import com.fixflow.notify.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Provider-independent notifications. Channels write to the outbox; the default
 * provider is LOG so the product works without email or WhatsApp credentials.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    public static final List<String> DEFAULT_EVENTS = List.of(
            "PASSWORD_RESET", "PHONE_OTP", "EMAIL_OTP", "WHATSAPP_OTP", "MAGIC_LINK",
            "USER_INVITED", "JOIN_REQUEST_APPROVED",
            "SALE_COMPLETED", "REPAIR_READY", "LOW_STOCK",
            "SUPPORT_REPLY", "SUPPORT_TICKET", "DEVICE_REVOKED");

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final FeatureFlagService featureFlagService;
    private final InboxNotificationRepository inboxNotificationRepository;
    private final PushDispatchService pushDispatchService;

    public void emit(UUID shopId, UUID userId, String eventType, String recipient, String subject, String body) {
        NotificationPreference prefs = userId == null
                ? defaultPreference(null, eventType)
                : preferenceRepository.findByUserIdAndEventType(userId, eventType)
                .orElseGet(() -> defaultPreference(userId, eventType));

        write(shopId, userId, eventType, "LOG", recipient, subject, body, "SENT", "log", null);

        if (prefs.isEmail() && recipient != null && recipient.contains("@")) {
            write(shopId, userId, eventType, "EMAIL", recipient, subject, body, "SENT", "log-email", null);
        }
        boolean whatsappOn = featureFlagService.enabled(shopId, "WHATSAPP_ENABLED") && prefs.isWhatsapp();
        if (whatsappOn && recipient != null) {
            write(shopId, userId, eventType, "WHATSAPP", recipient, subject, body, "SENT", "log-whatsapp", null);
        }
        if (prefs.isSms() && recipient != null) {
            write(shopId, userId, eventType, "SMS", recipient, subject, body, "QUEUED", "dev-sms", null);
        }
        if (userId != null && !isAuthSecret(eventType)) {
            InboxNotification inbox = new InboxNotification();
            inbox.setShopId(shopId);
            inbox.setUserId(userId);
            inbox.setEventType(eventType);
            inbox.setTitle(subject == null ? eventType : subject);
            inbox.setBody(body);
            inboxNotificationRepository.save(inbox);
            write(shopId, userId, eventType, "INBOX", recipient, subject, body, "SENT", "inbox", null);
            if (prefs.isPush()) {
                write(shopId, userId, eventType, "PUSH", recipient, subject, body, "QUEUED", "expo", null);
                pushDispatchService.sendToUser(userId, subject == null ? eventType : subject, body, null);
            }
        }
        log.info("Notification {} to {}: {}", eventType, recipient, subject);
    }

    public static boolean isAuthSecret(String eventType) {
        return "PASSWORD_RESET".equals(eventType) || "MAGIC_LINK".equals(eventType)
                || "EMAIL_OTP".equals(eventType) || "PHONE_OTP".equals(eventType)
                || "WHATSAPP_OTP".equals(eventType);
    }

    public static String redactedBody(String eventType, String body) {
        return isAuthSecret(eventType) ? "Delivered through a private channel." : body;
    }

    public void record(UUID shopId, UUID userId, String eventType, String channel, String recipient,
                       String subject, String body, String status, String provider, String providerRef) {
        write(shopId, userId, eventType, channel, recipient, subject, body, status, provider, providerRef);
    }

    public NotificationPreference defaultPreference(UUID userId, String eventType) {
        NotificationPreference prefs = new NotificationPreference();
        prefs.setUserId(userId);
        prefs.setEventType(eventType);
        prefs.setEmail(true);
        prefs.setPush(true);
        prefs.setWhatsapp(false);
        prefs.setSms(false);
        return prefs;
    }

    private void write(UUID shopId, UUID userId, String eventType, String channel, String recipient,
                       String subject, String body, String status, String provider, String providerRef) {
        NotificationOutbox row = new NotificationOutbox();
        row.setShopId(shopId);
        row.setUserId(userId);
        row.setEventType(eventType);
        row.setChannel(channel);
        row.setRecipient(recipient);
        row.setSubject(subject);
        row.setBody(redactedBody(eventType, body));
        row.setStatus(status);
        row.setProvider(provider);
        row.setProviderRef(providerRef);
        row.setSentAt("SENT".equals(status) ? Instant.now() : null);
        outboxRepository.save(row);
    }
}
