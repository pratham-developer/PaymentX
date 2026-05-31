package com.pratham.paymentx.service;

import java.util.UUID;

public interface MerchantApprovalService {
    void processApproval(UUID merchantId);
    String transitionToProcessing(UUID merchantId);
    void activateMerchant(UUID merchantId);
    void failMerchant(UUID merchantId);
}