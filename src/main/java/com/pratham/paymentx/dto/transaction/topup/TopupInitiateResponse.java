package com.pratham.paymentx.dto.transaction.topup;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class TopupInitiateResponse {
    private UUID transactionId;
    private String paymentSessionId;
}