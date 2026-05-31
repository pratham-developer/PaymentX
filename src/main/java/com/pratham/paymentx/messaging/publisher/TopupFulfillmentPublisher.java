package com.pratham.paymentx.messaging.publisher;

import com.pratham.paymentx.messaging.config.RabbitMQConfig;
import com.pratham.paymentx.messaging.event.TopupFulfillmentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TopupFulfillmentPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(UUID transactionId) {
        TopupFulfillmentEvent event = new TopupFulfillmentEvent(transactionId);
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EVENTS_EXCHANGE,
                RabbitMQConfig.TOPUP_FULFILLMENT_ROUTING_KEY,
                event
        );
        log.info("Published TopupFulfillmentEvent for txId={}", transactionId);
    }
}