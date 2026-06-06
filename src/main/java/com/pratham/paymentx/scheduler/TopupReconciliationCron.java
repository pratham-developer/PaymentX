package com.pratham.paymentx.scheduler;

import com.pratham.paymentx.enums.TransactionStatus;
import com.pratham.paymentx.enums.TransactionType;
import com.pratham.paymentx.messaging.publisher.TopupFulfillmentPublisher;
import com.pratham.paymentx.repository.TransactionRepository;
import com.pratham.paymentx.service.TopupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class TopupReconciliationCron {

    private final TransactionRepository transactionRepository;
    private final TopupFulfillmentPublisher topupPublisher;
    private final TopupService topupService;

    // Runs every 5 minutes
    @Scheduled(cron = "0 */5 * * * *")
    public void sweepStaleTopups() {
        OffsetDateTime now = OffsetDateTime.now();

        // 1. RECOVERY SWEEP (query for 6 mins to 24 hours old)
        List<UUID> staleIds = transactionRepository.findStalePendingTopupIds(
                TransactionType.TOPUP,
                TransactionStatus.PENDING,
                now.minusHours(24),
                now.minusMinutes(6)
        );

        if (!staleIds.isEmpty()) {
            log.info("Reconciliation Cron: Enqueueing {} stale top-ups for verification.", staleIds.size());
            for (UUID txId : staleIds) {
                topupPublisher.publish(txId);
            }
        }

        // 2. GARBAGE COLLECTION SWEEP
        // Hard-fail abandoned records older than 24 hours to prevent DB bloat
        int expiredCount = topupService.markAsFailedIfOlderThan(
                TransactionType.TOPUP,
                TransactionStatus.PENDING,
                TransactionStatus.FAILED,
                now.minusHours(24)
        );

        if (expiredCount > 0) {
            log.info("Reconciliation Cron: Hard-failed {} abandoned top-ups older than 24 hours.", expiredCount);
        }
    }
}