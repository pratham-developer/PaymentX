package com.pratham.paymentx.service;

import com.pratham.paymentx.dto.admin.CreateAdminRequest;
import com.pratham.paymentx.dto.admin.MerchantSummaryResponse;
import com.pratham.paymentx.enums.MerchantGatewayStatus;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface AdminService {
    void createAdmin(CreateAdminRequest request);
    void approveMerchant(UUID merchantId);
    Page<MerchantSummaryResponse> getMerchants(MerchantGatewayStatus status, int page, int size);
}
