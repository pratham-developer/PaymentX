package com.pratham.paymentx.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pratham.paymentx.entity.Transaction;
import com.pratham.paymentx.exception.BadRequestException;
import com.pratham.paymentx.messaging.publisher.PayoutReconciliationPublisher;
import com.pratham.paymentx.repository.TransactionRepository;
import com.pratham.paymentx.service.CashfreePayoutWebhookService;
import com.pratham.paymentx.util.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashfreePayoutWebhookServiceImpl implements CashfreePayoutWebhookService {

    private final PayoutReconciliationPublisher payoutPublisher;
    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;
    private final HashUtil hashUtil;

    @Value("${cashfree.payout.client-secret}")
    private String clientSecret;

    @Override
    public void processWebhook(String rawBody) {
        try {
            Map<String, String> payloadMap = new HashMap<>();

            // 1. UNIVERSAL PARSER: Handles both JSON and URL-Encoded Form Data
            if (rawBody.trim().startsWith("{")) {
                payloadMap = objectMapper.readValue(rawBody, new TypeReference<>() {});
            } else {
                String[] pairs = rawBody.split("&");
                for (String pair : pairs) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2) {
                        payloadMap.put(
                                URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                                URLDecoder.decode(kv[1], StandardCharsets.UTF_8)
                        );
                    } else if (kv.length == 1) {
                        payloadMap.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), "");
                    }
                }
            }

            // 2. Extract and Validate Signature
            String providedSignature = payloadMap.remove("signature");
            if (providedSignature == null || providedSignature.isBlank()) {
                log.warn("SECURITY DROP: Webhook payload missing signature parameter.");
                throw new BadRequestException("Missing signature");
            }

            // 3. Sort keys alphabetically and concatenate values
            Map<String, String> sortedMap = payloadMap.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (oldValue, newValue) -> oldValue,
                            LinkedHashMap::new
                    ));

            String postDataString = sortedMap.values().stream()
                    .filter(val -> val != null && !val.isEmpty())
                    .collect(Collectors.joining());

            // 4. Compute Signature
            String computedSignature = hashUtil.hash(postDataString, clientSecret);

            // 5. Verify Match (Accounting for Base64 '+' to ' ' URL encoding quirks)
            if (!computedSignature.equals(providedSignature) && !computedSignature.equals(providedSignature.replace(" ", "+"))) {
                log.error("SECURITY ALERT: Invalid Cashfree payout webhook signature detected.");
                throw new BadRequestException("Invalid signature");
            }

            // 6. Queue the Internal Transaction ID
            String transferId = payloadMap.get("transferId"); // This is the idempotencyKey
            if (transferId != null && !transferId.isEmpty()) {

                // Translate Gateway ID -> Internal DB ID
                Transaction tx = transactionRepository.findByIdempotencyKey(UUID.fromString(transferId))
                        .orElseThrow(() -> new BadRequestException("Transaction not found for idempotency key"));

                payoutPublisher.publish(tx.getId());
                log.info("Webhook successfully verified and queued for internal txId: {}", tx.getId());
            } else {
                log.warn("Webhook verified but 'transferId' was missing from payload.");
            }

        } catch (Exception e) {
            log.error("Failed to process payout webhook payload", e);
            throw new BadRequestException("Invalid webhook payload");
        }
    }
}