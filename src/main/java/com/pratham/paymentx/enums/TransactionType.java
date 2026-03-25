package com.pratham.paymentx.enums;

public enum TransactionType {
    TOPUP,
    PURCHASE,
    WITHDRAWAL,
    SETTLEMENT,
    REFUND
}

//TODO: refund for failures, settlement for auto payout cron job, withdrawal for manual payout
