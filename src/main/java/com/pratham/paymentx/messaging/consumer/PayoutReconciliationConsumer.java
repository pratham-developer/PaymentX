package com.pratham.paymentx.messaging.consumer;

import com.pratham.paymentx.messaging.config.RabbitMQConfig;
import com.pratham.paymentx.service.PayoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PayoutReconciliationConsumer {
    private final PayoutService payoutService;

    @RabbitListener(queues = RabbitMQConfig.PAYOUT_RECON_QUEUE)
    public void consume(UUID transactionId) {
        log.info("MQ Worker picked up payout reconciliation for txId: {}", transactionId);
        try {
            payoutService.processPayoutReconciliation(transactionId);
        } catch (Exception e) {
            log.error("Reconciliation failed for txId: {}. Requeueing...", transactionId, e);
            throw e; // Triggers RabbitMQ backoff & retry
        }
    }
}