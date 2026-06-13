package com.pratham.paymentx.scheduler;

import com.pratham.paymentx.enums.TransactionStatus;
import com.pratham.paymentx.enums.TransactionType;
import com.pratham.paymentx.messaging.publisher.PayoutReconciliationPublisher;
import com.pratham.paymentx.repository.TransactionRepository;
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
public class PayoutReconciliationCron {

    private final TransactionRepository transactionRepository;
    private final PayoutReconciliationPublisher payoutPublisher;

    @Scheduled(cron = "0 */15 * * * *")
    public void sweepStuckPayouts() {
        OffsetDateTime now = OffsetDateTime.now();

        // Fetch max 1000 records to prevent JVM memory spikes
        List<UUID> stuckPayoutIds = transactionRepository.findStaleTransactionIds(
                TransactionType.PAYOUT,
                TransactionStatus.PENDING,
                now.minusDays(3),
                now.minusMinutes(15),
                PageRequest.of(0, 1000)
        );

        if (!stuckPayoutIds.isEmpty()) {
            log.info("Payout Cron: Enqueueing {} stuck payouts for Cashfree verification.", stuckPayoutIds.size());
            for (UUID txId : stuckPayoutIds) {
                payoutPublisher.publish(txId);
            }
        }
    }
}