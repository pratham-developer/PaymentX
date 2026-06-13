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
public class AdminEodCron {

    private final FinancialReportingService financialReportingService;

    // Runs at 1:00 AM every day
    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Kolkata")
    public void executeAdminReport() {
        log.info("CRON TRIGGERED: Starting Admin EOD Report generation...");

        // The target date is "Yesterday" because we are summarizing the day that just ended.
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(1);

        try {
            financialReportingService.generateAndSendAdminEodReport(yesterday);
            log.info("CRON FINISHED: Admin EOD Report completed.");
        } catch (Exception e) {
            log.error("CRITICAL CRON FAILURE: Could not generate Admin EOD Report", e);
        }
    }
}