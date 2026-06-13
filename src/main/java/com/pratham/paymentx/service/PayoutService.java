package com.pratham.paymentx.service;

import com.pratham.paymentx.entity.Transaction;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface PayoutService {
    // Core External Operations
    void initiateInstantPayout(UUID merchantUserId, BigDecimal amount);
    void processNightlyBatchSettlements();
    void processPayoutReconciliation(UUID transactionId);

    // Internal Isolated Transaction Boundaries
    Transaction quarantineInstantPayoutFunds(UUID merchantUserId, BigDecimal amount, UUID idempotencyKey);
    Optional<Transaction> quarantineFullBalanceForStandardPayout(UUID merchantUserId, UUID idempotencyKey);
    void settleSuccessfulPayout(UUID transactionId);
    void refundFailedPayout(UUID transactionId);
}