package com.pratham.paymentx.dto.transaction.payout;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class PayoutInitiateRequest {
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "10.00", message = "Minimum withdrawal amount is ₹10.00")
    private BigDecimal amount;
}