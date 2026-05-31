package com.pratham.paymentx.service;

import com.pratham.paymentx.dto.transaction.TopupInitiateRequest;
import com.pratham.paymentx.dto.transaction.TopupInitiateResponse;
import com.pratham.paymentx.entity.Transaction;

import java.util.UUID;

public interface TopupService {

    // ─── Public API ───
    TopupInitiateResponse initiateTopup(UUID userId, TopupInitiateRequest request);
    void verifyTransactionOwnership(UUID transactionId, UUID userId);
    void executeFulfillmentEngine(UUID transactionId);

    // ─── Isolated Transaction Boundaries for Orchestrator ───
    Transaction createPendingTopupRecord(UUID userId, TopupInitiateRequest request);
    boolean settlePaidTopup(UUID transactionId);
    void settleFailedTopup(UUID transactionId);
}