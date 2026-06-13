package com.pratham.paymentx.messaging.consumer;

import com.pratham.paymentx.messaging.config.RabbitMQConfig;
import com.pratham.paymentx.messaging.event.EmailNotificationEvent;
import com.pratham.paymentx.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailConsumer {

    private final EmailService emailService;

    // "concurrency = 3-5" means a max of 5 emails are processed concurrently.
    @RabbitListener(queues = RabbitMQConfig.EMAIL_QUEUE, concurrency = "3-5")
    public void consumeEmailEvent(EmailNotificationEvent event) {
        try {
            emailService.processEmailEvent(event);
        } catch (Exception e) {
            log.error("Email worker failed for {}. Sending to DLQ retry queue.", event.toEmail());
            throw e;
        }
    }
}