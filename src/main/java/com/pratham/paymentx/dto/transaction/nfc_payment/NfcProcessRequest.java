package com.pratham.paymentx.dto.transaction.nfc_payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class NfcProcessRequest {

    @NotNull(message = "Session token is required")
    private UUID sessionToken;

    @NotBlank(message = "PIN is required")
    private String pin;
}