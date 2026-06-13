package com.pratham.paymentx.scheduler;

import com.pratham.paymentx.enums.TransactionStatus;
import com.pratham.paymentx.enums.TransactionType;
import com.pratham.paymentx.messaging.publisher.TopupFulfillmentPublisher;
import com.pratham.paymentx.repository.TransactionRepository;
import com.pratham.paymentx.service.TopupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
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

    @Scheduled(cron = "0 */5 * * * *")
    public void sweepStaleTopups() {
        OffsetDateTime now = OffsetDateTime.now();

        // Fetch max 1000 records to prevent JVM memory spikes
        List<UUID> staleIds = transactionRepository.findStaleTransactionIds(
                TransactionType.TOPUP,
                TransactionStatus.PENDING,
                now.minusHours(24),
                now.minusMinutes(6),
                PageRequest.of(0, 1000)
        );

        if (!staleIds.isEmpty()) {
            log.info("Reconciliation Cron: Enqueueing {} stale top-ups for verification.", staleIds.size());
            for (UUID txId : staleIds) {
                topupPublisher.publish(txId);
            }
        }

        // Garbage collection sweep (Leaves untouched, it executes an UPDATE query directly on DB)
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