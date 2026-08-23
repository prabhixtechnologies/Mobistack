package com.fixflow.notify;

import com.fixflow.common.error.ApiException;
import com.fixflow.config.FixFlowProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpDeliveryServiceTest {

    @Mock
    private TwilioGateway twilio;
    @Mock
    private NotificationService notificationService;

    private FixFlowProperties properties;
    private MockEnvironment environment;

    @BeforeEach
    void setUp() {
        properties = new FixFlowProperties();
        properties.getAuth().setDevOtp("654321");
        environment = new MockEnvironment();
    }

    @Test
    void prodWithoutTwilioFailsClosed() {
        environment.setActiveProfiles("prod");
        when(twilio.canSms()).thenReturn(false);
        OtpDeliveryService service = new OtpDeliveryService(twilio, notificationService, properties, environment);
        assertThatThrownBy(() -> service.sendPhone("9876543210", false, null))
                .isInstanceOf(ApiException.class);
        verify(notificationService, never()).emit(any(), any(), any(), any(), any(), any());
    }

    @Test
    void devWithoutTwilioIssuesCodeWithoutPersistingSecret() {
        environment.setActiveProfiles("dev");
        when(twilio.canSms()).thenReturn(false);
        OtpDeliveryService service = new OtpDeliveryService(twilio, notificationService, properties, environment);
        OtpDeliveryService.Delivery delivery = service.sendPhone("9876543210", false, null);
        assertThat(delivery.code()).isEqualTo("654321");
        assertThat(delivery.live()).isFalse();
        verify(notificationService).emit(any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.argThat(body -> !body.contains("654321")));
    }
}
