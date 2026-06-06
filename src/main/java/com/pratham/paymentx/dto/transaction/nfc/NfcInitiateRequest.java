package com.pratham.paymentx.dto.transaction.nfc;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class NfcInitiateRequest {
    @NotBlank(message = "Chip ID is required")
    private String chipId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Amount must be at least ₹1.00")
    private BigDecimal amount;
}