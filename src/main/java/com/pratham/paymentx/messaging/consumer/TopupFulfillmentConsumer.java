package com.pratham.paymentx.messaging.consumer;

import com.pratham.paymentx.messaging.config.RabbitMQConfig;
import com.pratham.paymentx.messaging.event.TopupFulfillmentEvent;
import com.pratham.paymentx.service.TopupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TopupFulfillmentConsumer {

    private final TopupService topupService;

    @RabbitListener(queues = RabbitMQConfig.TOPUP_FULFILLMENT_QUEUE)
    public void onTopupFulfillment(TopupFulfillmentEvent event) {
        log.info("MQ Worker picked up topup fulfillment for txId={}", event.transactionId());
        topupService.executeFulfillmentEngine(event.transactionId());
    }
}