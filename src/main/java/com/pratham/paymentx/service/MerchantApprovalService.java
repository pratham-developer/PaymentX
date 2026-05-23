package com.pratham.paymentx.service;

import com.pratham.paymentx.entity.MerchantProfile;

import java.util.UUID;

public interface MerchantApprovalService {

    void processApproval(UUID merchantId);

    void transitionToProcessing(MerchantProfile merchant);

    void activateMerchant(MerchantProfile merchant);

    void failMerchant(MerchantProfile merchant);
}