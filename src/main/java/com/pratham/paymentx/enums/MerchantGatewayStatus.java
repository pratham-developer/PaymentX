package com.pratham.paymentx.enums;

/*
 * Tracks the state of a merchant's Cashfree payout gateway registration.
 *
 * State machine:
 *
 *   PENDING
 *     │  Admin clicks approve → RabbitMQ message published
 *     ▼
 *   PROCESSING
 *     │  Consumer calls POST /beneficiary on Cashfree
 *     │  Response: beneficiary_status = VERIFIED
 *     ▼
 *   BENEFICIARY_CREATED
 *     │  Admin activates merchant (profileActive = true)
 *     ▼
 *   COMPLETED  ──────────────────────────────────────────────┐
 *     │  Cron job picks up merchant, initiates payout        │
 *     │  Cashfree webhook: TRANSFER_SUCCESS + COMPLETED      │
 *     └──────────────────────────────────────────────────────┘
 *
 *   PROCESSING ──► FAILED
 *     Cashfree returns beneficiary_status = INVALID or FAILED.
 *     Bank details are wiped, profileCompleted set to false.
 *     Merchant must re-enter correct details.
 *
 *   COMPLETED ──► QUARANTINED
 *     Cashfree webhook: TRANSFER_FAILED or TRANSFER_REVERSED.
 *     availableBalance is restored from processingBalance.
 *     cashfreeBeneficiaryId is nullified (stale beneficiary deleted from Cashfree).
 *     gatewayStatus set to QUARANTINED, profileCompleted set to false.
 *     Cron job skips QUARANTINED merchants entirely.
 *     Merchant must re-enter correct bank details → flows back through PENDING.
 */
public enum MerchantGatewayStatus {
    PENDING,
    PROCESSING,
    BENEFICIARY_CREATED,
    COMPLETED,
    FAILED,
    QUARANTINED
}