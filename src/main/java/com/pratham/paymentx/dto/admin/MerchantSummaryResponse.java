package com.pratham.paymentx.dto.admin;

import com.pratham.paymentx.enums.MerchantGatewayStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class MerchantSummaryResponse {
    private UUID merchantId;
    private String email;
    private String businessName;
    private String phone;
    private String beneficiaryName;
    private MerchantGatewayStatus gatewayStatus;
    private OffsetDateTime createdAt;
}