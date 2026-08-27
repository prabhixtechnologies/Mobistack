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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
            "USER_INVITED", "JOIN_REQUEST", "JOIN_REQUEST_APPROVED", "JOIN_REQUEST_CANCELLED",
            "SALE_COMPLETED", "REPAIR_READY", "LOW_STOCK",
            "SUPPORT_REPLY", "SUPPORT_TICKET", "DEVICE_REVOKED",
            "PAYMENT_PENDING", "PAYMENT_RECEIVED", "PAYMENT_FAILED", "BILLING_REMINDER");

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final FeatureFlagService featureFlagService;
    private final InboxNotificationRepository inboxNotificationRepository;
    private final PushDispatchService pushDispatchService;

    public void emit(UUID shopId, UUID userId, String eventType, String recipient, String subject, String body) {
        emit(shopId, userId, eventType, recipient, subject, body, null);
    }

    /**
     * Records an event and delivers it on every channel the person has left on.
     *
     * @param link in-app path the alert should open, or null for the inbox
     */
    public void emit(UUID shopId, UUID userId, String eventType, String recipient, String subject, String body,
                     String link) {
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
            inbox.setLink(link);
            inboxNotificationRepository.save(inbox);
            write(shopId, userId, eventType, "INBOX", recipient, subject, body, "SENT", "inbox", null);
            if (prefs.isPush()) {
                UUID pushRow = write(shopId, userId, eventType, "PUSH", recipient, subject, body,
                        "QUEUED", "expo", null);
                String title = subject == null ? eventType : subject;
                // Sent after the surrounding business transaction commits: a push
                // about a sale that then rolled back is worse than a late one, and
                // an outbound call must not hold a database connection open.
                afterCommit(() -> deliverPush(pushRow, userId, title, body, link));
            }
        }
        log.info("Notification {} to {}: {}", eventType, recipient, subject);
    }

    private void deliverPush(UUID outboxId, UUID userId, String title, String body, String link) {
        PushDispatchService.Delivery delivery = pushDispatchService.sendToUser(userId, title, body, link);
        outboxRepository.findById(outboxId).ifPresent(row -> {
            row.setStatus(delivery.status());
            row.setProviderRef(delivery.detail());
            row.setSentAt("SENT".equals(delivery.status()) ? Instant.now() : null);
            outboxRepository.save(row);
        });
    }

    /** Runs after the caller's transaction commits, or immediately if there is none. */
    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    /**
     * Events whose body carries a credential. These never reach the inbox, push
     * or the outbox in readable form.
     *
     * <p>{@code USER_INVITED} belongs here: its body contains the join token, and
     * the outbox is readable by every member of the shop. A junior member could
     * otherwise read an invitation addressed to a new admin and claim it.
     */
    public static boolean isAuthSecret(String eventType) {
        return "PASSWORD_RESET".equals(eventType) || "MAGIC_LINK".equals(eventType)
                || "EMAIL_OTP".equals(eventType) || "PHONE_OTP".equals(eventType)
                || "WHATSAPP_OTP".equals(eventType) || "USER_INVITED".equals(eventType);
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

    private UUID write(UUID shopId, UUID userId, String eventType, String channel, String recipient,
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
        return outboxRepository.save(row).getId();
    }
}
