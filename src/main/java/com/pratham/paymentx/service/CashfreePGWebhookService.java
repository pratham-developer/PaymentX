package com.pratham.paymentx.service;

public interface CashfreePGWebhookService {
    void processWebhook(String signature, String timestamp, String rawBody);
}
