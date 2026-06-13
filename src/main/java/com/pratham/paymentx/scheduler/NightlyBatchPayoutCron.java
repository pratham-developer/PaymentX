package com.pratham.paymentx.scheduler;

import com.pratham.paymentx.service.PayoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NightlyBatchPayoutCron {
    private final PayoutService payoutService;

    // Runs at 11:59 PM every night
    @Scheduled(cron = "0 59 23 * * *", zone = "Asia/Kolkata")
    public void executeNightlySettlements() {
        log.info("CRON TRIGGERED: Starting Nightly Batch Payouts...");
        payoutService.processNightlyBatchSettlements();
        log.info("CRON FINISHED: Nightly Batch Payouts completed.");
    }
}