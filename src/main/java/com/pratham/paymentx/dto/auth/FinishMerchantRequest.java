package com.pratham.paymentx.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FinishMerchantRequest {

    @NotBlank(message = "Business name is mandatory")
    @Size(min = 2, max = 100, message = "Business name must be between 2 and 100 characters")
    private String businessName;

    @NotBlank(message = "Phone number is mandatory")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format (E.164 required)")
    private String phone;

    @NotBlank(message = "GST Tax ID is mandatory")
    @Pattern(regexp = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$", message = "Invalid GSTIN format")
    private String gstTaxId;

    @NotBlank(message = "Bank Account Number is mandatory")
    @Pattern(regexp = "^\\d{9,18}$", message = "Invalid bank account number format")
    private String bankAccountNumber;

    @NotBlank(message = "IFSC code is mandatory")
    @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "Invalid IFSC format")
    private String ifscCode;

    @NotBlank(message = "Beneficiary name is mandatory")
    @Size(min = 2, max = 100, message = "Beneficiary name must be between 2 and 100 characters")
    private String beneficiaryName;
}