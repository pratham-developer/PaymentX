package com.pratham.paymentx.entity;

import com.pratham.paymentx.enums.MerchantGatewayStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MerchantProfile {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 100)
    private String businessName;

    @Column(length = 20)
    private String phone;

    /*
     * The ID we register with Cashfree when creating a beneficiary.
     * Format: "MERCHANT-{userId}" — stable and unique per merchant.
     *
     * This doubles as our idempotency key for the beneficiary creation call:
     * if the admin approval job retries (e.g. after a crash), Cashfree returns
     * 409 beneficiary_id_already_exists and we skip creation, move straight to
     * checking beneficiary status.
     *
     * Set during the admin approval flow (PROCESSING → BENEFICIARY_CREATED).
     * Nullified if the merchant enters invalid bank details and must re-onboard
     * (status → QUARANTINED, profileCompleted → false).
     *
     * updatable = false is intentionally NOT set here — we need to null it out
     * on quarantine so the merchant can re-register a corrected beneficiary.
     */
    @Column(unique = true, length = 100)
    private String cashfreeBeneficiaryId;

    @Enumerated(value = EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MerchantGatewayStatus gatewayStatus = MerchantGatewayStatus.PENDING;

    @Column(columnDefinition = "bytea")
    private byte[] encryptedGstTaxId;

    @Column(columnDefinition = "bytea")
    private byte[] encryptedBankAccount;

    @Column(columnDefinition = "bytea")
    private byte[] encryptedIfsc;

    /*
     * Name of the bank account holder as provided by the merchant.
     * Sent to Cashfree as beneficiary_name.
     * Not encrypted — not sensitive on its own.
     */
    @Column(length = 100)
    private String beneficiaryName;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}