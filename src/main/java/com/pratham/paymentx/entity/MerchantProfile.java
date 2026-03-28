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

    private String razorpayContactId;
    private String razorpayFundId;

    @Enumerated(value = EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MerchantGatewayStatus gatewayStatus = MerchantGatewayStatus.PENDING;

    //will be mapped to Razorpay contact creation reference id
    //Razorpay ensures if one reference id has already created a contact
    //so on retries with the same reference id, Razorpay will directly return the previously created contact
    //instead of re-creating one again
    @Column(unique = true, updatable = false, length = 100)
    private String idempotencyKey;

    //encrypted details
    @Column(columnDefinition = "bytea")
    private byte[] encryptedGstTaxId;

    @Column(columnDefinition = "bytea")
    private byte[] encryptedBankAccount;

    @Column(columnDefinition = "bytea")
    private byte[] encryptedIfsc;

    @Column(length = 100)
    private String beneficiaryName;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}

