package com.pratham.paymentx.service;

import com.pratham.paymentx.dto.admin.CreateAdminRequest;

import java.util.UUID;

public interface AdminService {
    void createAdmin(CreateAdminRequest request);
    void approveMerchant(UUID merchantId);
}
