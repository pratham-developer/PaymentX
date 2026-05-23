package com.pratham.paymentx.messaging.consumer;

import com.pratham.paymentx.messaging.config.RabbitMQConfig;
import com.pratham.paymentx.messaging.event.MerchantApprovalEvent;
import com.pratham.paymentx.service.MerchantApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MerchantApprovalConsumer {

    private final MerchantApprovalService merchantApprovalService;

    @RabbitListener(queues = RabbitMQConfig.MERCHANT_APPROVAL_QUEUE)
    public void onMerchantApproval(MerchantApprovalEvent event) {
        log.info("Received MerchantApprovalEvent for merchantId={}", event.merchantId());
        merchantApprovalService.processApproval(event.merchantId());
        log.info("MerchantApprovalEvent processed for merchantId={}", event.merchantId());
    }
}