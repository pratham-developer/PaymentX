package com.pratham.paymentx.scheduler;

import com.pratham.paymentx.service.FinancialReportingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
@Slf4j
public class MerchantStatementCron {

    private final FinancialReportingService financialReportingService;

    // Runs at 6:00 AM IST every day
    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Kolkata")
    public void executeMerchantStatements() {
        log.info("CRON TRIGGERED: Starting Merchant Daily Statements...");

        // Target Date is yesterday
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(1);

        try {
            financialReportingService.generateAndSendMerchantDailyStatements(yesterday);
            log.info("CRON FINISHED: Merchant Daily Statements queued successfully.");
        } catch (Exception e) {
            log.error("CRITICAL CRON FAILURE: Could not generate Merchant Statements", e);
        }
    }
}