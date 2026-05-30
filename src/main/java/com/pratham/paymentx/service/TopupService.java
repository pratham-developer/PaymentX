package com.pratham.paymentx.service;

import com.pratham.paymentx.dto.transaction.TopupInitiateRequest;
import com.pratham.paymentx.dto.transaction.TopupInitiateResponse;

import java.util.UUID;

public interface TopupService {
    TopupInitiateResponse initiateTopup(UUID userId, TopupInitiateRequest request);
    void executeFulfillmentEngine(UUID transactionId);
}