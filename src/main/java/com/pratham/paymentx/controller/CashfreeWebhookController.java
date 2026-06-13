package com.pratham.paymentx.controller;

import com.pratham.paymentx.service.CashfreePGWebhookService;
import com.pratham.paymentx.service.CashfreePayoutWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhook/cashfree")
@RequiredArgsConstructor
@Slf4j
public class CashfreeWebhookController {

    private final CashfreePGWebhookService cashfreePGWebhookService;
    private final CashfreePayoutWebhookService cashfreePayoutWebhookService;

    @PostMapping("/payment")
    public ResponseEntity<Void> handlePGWebhook(
            @RequestHeader(value = "x-webhook-signature", required = false) String signature,
            @RequestHeader(value = "x-webhook-timestamp", required = false) String timestamp,
            @RequestBody String rawBody) {
        log.info("Received Payment Gateway Webhook Request");
        if (signature == null || timestamp == null) {
            return ResponseEntity.badRequest().build();
        }
        cashfreePGWebhookService.processWebhook(signature, timestamp, rawBody);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/payout")
    public ResponseEntity<Void> handlePayoutWebhook(@RequestBody String rawBody) {
        log.info("Received Payout Webhook Request");
        cashfreePayoutWebhookService.processWebhook(rawBody);
        return ResponseEntity.ok().build();
    }
}