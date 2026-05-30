package com.pratham.paymentx.entity;

import com.pratham.paymentx.enums.WalletStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
@Builder
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, unique = true)
    private User user;

    /*
     * Dual-balance system:
     *
     * availableBalance — funds the user/merchant can actually spend or withdraw.
     *                    Decremented when a settlement cron job initiates a payout.
     *
     * processingBalance — funds currently in-flight with Cashfree.
     *                     Incremented when the cron job triggers a payout (mirrors
     *                     the deduction from availableBalance).
     *                     Decremented when Cashfree fires a terminal webhook:
     *                       - TRANSFER_SUCCESS/COMPLETED  → deduct (money truly left)
     *                       - TRANSFER_FAILED/REVERSED    → move back to availableBalance
     *
     * Total balance (for display) = availableBalance + processingBalance.
     * Computed on the fly in the DTO — never stored as a separate column.
     */
    @Column(precision = 19, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(precision = 19, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal processingBalance = BigDecimal.ZERO;

    /*
     * PIN is stored as an HMAC-SHA256 hash (same HashUtil used for refresh tokens).
     * null until the user explicitly creates their wallet via POST /wallet/create.
     */
    private String pinHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletStatus walletStatus;

    /*
     * Tracks consecutive failed PIN attempts.
     * Reset to 0 on a successful PIN entry.
     * Wallet transitions to LOCKED after a configured threshold (e.g. 5).
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer pinAttempts = 0;

    /*
     * Optimistic lock for concurrent balance updates (e.g. simultaneous NFC taps).
     * The settlement cron job uses pessimistic locking instead — see WalletRepository.
     */
    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @CreationTimestamp
    @Column(updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    private OffsetDateTime updatedAt;
}