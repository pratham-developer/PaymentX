package com.pratham.paymentx.dto.transaction.nfc_payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NfcRedisSession {
    private UUID studentWalletId;
    private UUID merchantWalletId;
    private UUID merchantUserId;
    private BigDecimal amount;
}