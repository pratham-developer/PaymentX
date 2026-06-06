package com.pratham.paymentx.dto.transaction.nfc;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class NfcInitiateResponse {
    private String nfcSessionToken;
    private long expiresIn;
    private String studentName;
    private String walletStatus;
    private BigDecimal amount;
}