package com.fixflow.billing.job;

import com.fixflow.billing.service.BillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BillingReminderJob {

    private final BillingService billingService;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Kolkata")
    public void run() {
        int expired = billingService.expireLapsedPlans();
        int reminded = billingService.sendDueReminders();
        if (expired > 0 || reminded > 0) {
            log.info("Billing job expired {} plans and sent {} reminders", expired, reminded);
        }
    }
}
