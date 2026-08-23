package com.fixflow.notify;

import com.fixflow.common.error.ApiException;
import com.fixflow.config.FixFlowProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TwilioGatewayTest {

    private TwilioGateway gateway;

    @BeforeEach
    void setUp() {
        FixFlowProperties properties = new FixFlowProperties();
        properties.getTwilio().setDefaultCountryCode("91");
        gateway = new TwilioGateway(properties);
    }

    @Test
    void tenDigitIndianNumberBecomesE164() {
        assertThat(gateway.toE164("9876543210")).isEqualTo("+919876543210");
        assertThat(gateway.toE164("09876543210")).isEqualTo("+919876543210");
        assertThat(gateway.toE164("+91 98765 43210")).isEqualTo("+919876543210");
        assertThat(gateway.toE164("whatsapp:+919876543210")).isEqualTo("+919876543210");
    }

    @Test
    void rejectsJunk() {
        assertThatThrownBy(() -> gateway.toE164("12")).isInstanceOf(ApiException.class);
    }
}
