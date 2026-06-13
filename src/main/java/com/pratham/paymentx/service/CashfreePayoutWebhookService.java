package com.pratham.paymentx.service;

public interface CashfreePayoutWebhookService {
    void processWebhook(String rawBody);
}