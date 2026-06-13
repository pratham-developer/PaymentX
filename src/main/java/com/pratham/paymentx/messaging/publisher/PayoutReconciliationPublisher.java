package com.pratham.paymentx.messaging.publisher;

import com.pratham.paymentx.messaging.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PayoutReconciliationPublisher {
    private final RabbitTemplate rabbitTemplate;

    public void publish(UUID transactionId) {
        log.info("Publishing Payout Reconciliation Event for txId: {}", transactionId);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EVENTS_EXCHANGE, RabbitMQConfig.PAYOUT_RECON_ROUTING_KEY, transactionId);
    }
}