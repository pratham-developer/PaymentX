package com.pratham.paymentx.messaging.publisher;

import com.pratham.paymentx.messaging.config.RabbitMQConfig;
import com.pratham.paymentx.messaging.event.EmailNotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(EmailNotificationEvent event) {
        log.debug("Publishing email event to MQ for: {}", event.toEmail());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EVENTS_EXCHANGE,
                RabbitMQConfig.EMAIL_ROUTING_KEY,
                event
        );
    }
}