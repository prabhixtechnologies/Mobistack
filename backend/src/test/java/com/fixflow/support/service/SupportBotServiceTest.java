package com.fixflow.support.service;

import com.fixflow.config.FixFlowProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupportBotServiceTest {

    private final SupportBotService bot = new SupportBotService(new FixFlowProperties());

    @Test
    void answersStockQuestionsWithoutEscalating() {
        var reply = bot.reply("How do I check stock for a barcode?", "Asha");
        assertThat(reply.escalate()).isFalse();
        assertThat(reply.body()).containsIgnoringCase("ledger");
    }

    @Test
    void escalatesWhenTheUserAsksForAPerson() {
        var reply = bot.reply("Please talk to a person", "Asha");
        assertThat(reply.escalate()).isTrue();
        assertThat(reply.body()).contains("Prabhix");
    }
}
