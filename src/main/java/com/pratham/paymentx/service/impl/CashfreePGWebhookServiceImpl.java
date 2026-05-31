package com.pratham.paymentx.service.impl;

import com.cashfree.pg.Cashfree;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pratham.paymentx.exception.BadRequestException;
import com.pratham.paymentx.messaging.publisher.TopupFulfillmentPublisher;
import com.pratham.paymentx.service.CashfreePGWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashfreePGWebhookServiceImpl implements CashfreePGWebhookService {
    private final Cashfree cashfree;
    private final ObjectMapper objectMapper;
    private final TopupFulfillmentPublisher topupPublisher;

    @Override
    public void processWebhook(String signature, String timestamp, String rawBody) {
        try {
            cashfree.PGVerifyWebhookSignature(signature, rawBody, timestamp);
        } catch (Exception e) {
            log.error("Security Alert: Invalid Cashfree PG Webhook Signature detected.");
            throw new BadRequestException("Invalid PG webhook signature");
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String eventType = root.path("type").asText("");
            String orderIdStr = root.path("data").path("order").path("order_id").asText(null);

            if (orderIdStr == null || orderIdStr.isBlank()) {
                return;
            }

            switch (eventType) {
                case "PAYMENT_SUCCESS_WEBHOOK",
                     "PAYMENT_FAILED_WEBHOOK",
                     "PAYMENT_USER_DROPPED_WEBHOOK" -> {
                    topupPublisher.publish(UUID.fromString(orderIdStr));
                }
                default -> log.debug("Ignored unhandled PG webhook event type: {} for order: {}", eventType, orderIdStr);
            }
        } catch (Exception e) {
            log.error("Failed to parse Cashfree PG webhook payload", e);
            throw new RuntimeException("PG Webhook processing failed", e);
        }
    }
}
