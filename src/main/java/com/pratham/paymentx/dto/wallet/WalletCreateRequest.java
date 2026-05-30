package com.pratham.paymentx.dto.wallet;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class WalletCreateRequest {

    @NotBlank(message = "PIN is required")
    @Pattern(regexp = "^\\d{4}$", message = "PIN must be exactly 4 digits")
    private String pin;

    @NotBlank(message = "Confirm PIN is required")
    @Pattern(regexp = "^\\d{4}$", message = "Confirm PIN must be exactly 4 digits")
    private String confirmPin;
}