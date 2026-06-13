package com.pratham.paymentx.projection;

import java.math.BigDecimal;

public interface MerchantFlowStats {
    BigDecimal getInflows();
    BigDecimal getOutflows();
    BigDecimal getFees();
}