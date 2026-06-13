package com.pratham.paymentx.service;

import com.pratham.paymentx.dto.transaction.topup.TopupInitiateRequest;
import com.pratham.paymentx.dto.transaction.topup.TopupInitiateResponse;
import com.pratham.paymentx.entity.Transaction;
import com.pratham.paymentx.enums.TransactionStatus;
import com.pratham.paymentx.enums.TransactionType;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface TopupService {

    //for public API
    TopupInitiateResponse initiateTopup(UUID userId, TopupInitiateRequest request);
    void verifyTransactionOwnership(UUID transactionId, UUID userId);
    void executeFulfillmentEngine(UUID transactionId);

    // for isolated transaction boundaries for orchestrator
    Transaction createPendingTopupRecord(UUID userId, TopupInitiateRequest request);
    boolean settlePaidTopup(UUID transactionId);
    void settleFailedTopup(UUID transactionId);

    // for topup reconciliation cron job
    int markAsFailedIfOlderThan(
            TransactionType type,
            TransactionStatus pendingStatus,
            TransactionStatus failedStatus,
            OffsetDateTime cutoffTime
    );
}