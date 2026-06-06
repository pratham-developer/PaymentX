package com.pratham.paymentx.dto.transaction.nfc;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NfcProcessRequest {
    @NotBlank(message = "Session token is required")
    private String sessionToken;

    @NotBlank(message = "PIN is required")
    private String pin;
}