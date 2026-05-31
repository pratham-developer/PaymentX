package com.pratham.paymentx.scheduler;

import com.pratham.paymentx.enums.TransactionStatus;
import com.pratham.paymentx.enums.TransactionType;
import com.pratham.paymentx.messaging.publisher.TopupFulfillmentPublisher;
import com.pratham.paymentx.repository.TransactionRepository;
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

    // Runs every 5 minutes
    @Scheduled(cron = "0 */5 * * * *")
    public void sweepStaleTopups() {
        OffsetDateTime now = OffsetDateTime.now();

        // Sweep anything older than 6 minutes (safely past the 5-min Cashfree expiry)
        List<UUID> staleIds = transactionRepository.findStalePendingTopupIds(
                TransactionType.TOPUP,
                TransactionStatus.PENDING,
                now.minusHours(24),
                now.minusMinutes(6)
        );

        if (!staleIds.isEmpty()) {
            log.info("Reconciliation Cron: Found {} stale pending top-ups. Enqueueing for verification.", staleIds.size());
            for (UUID txId : staleIds) {
                topupPublisher.publish(txId);
            }
        }
    }
}