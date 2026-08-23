package com.fixflow.notify;

import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailDeliveryService {

    private final MailGateway mailGateway;
    private final NotificationService notificationService;
    private final FixFlowProperties properties;
    private final Environment environment;

    public void send(UUID shopId, UUID userId, String eventType, String email, String subject, String body) {
        if (mailGateway.configured()) {
            mailGateway.send(email, subject, body);
            notificationService.record(shopId, userId, eventType, "EMAIL", email, subject,
                    "Delivered through a private channel.", "SENT", "smtp", null);
            return;
        }
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE,
                    "Email is not configured on this server. Set SMTP_HOST and SMTP_FROM.");
        }
        notificationService.emit(shopId, userId, eventType, email, subject,
                "An email would have been sent. Configure SMTP to deliver it.");
    }

    public String product() {
        return properties.getBrand().getProduct();
    }
}
