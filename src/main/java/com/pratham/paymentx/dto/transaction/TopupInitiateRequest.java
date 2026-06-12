package com.pratham.paymentx.dto.transaction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class TopupInitiateRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.0", message = "Minimum top-up amount is ₹1")
    private BigDecimal amount;

    @NotNull(message = "Idempotency key is required")
    private UUID idempotencyKey;
}