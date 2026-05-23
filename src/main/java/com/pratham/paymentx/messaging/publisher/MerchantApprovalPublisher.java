package com.pratham.paymentx.messaging.publisher;

import com.pratham.paymentx.messaging.config.RabbitMQConfig;
import com.pratham.paymentx.messaging.event.MerchantApprovalEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MerchantApprovalPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(UUID merchantId) {
        MerchantApprovalEvent event = new MerchantApprovalEvent(merchantId);
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EVENTS_EXCHANGE,
                RabbitMQConfig.MERCHANT_APPROVAL_ROUTING_KEY,
                event
        );
        log.info("Published MerchantApprovalEvent for merchantId={}", merchantId);
    }
}