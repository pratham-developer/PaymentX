package com.pratham.paymentx.messaging.event;

import java.util.UUID;

public record MerchantApprovalEvent(UUID merchantId) {}