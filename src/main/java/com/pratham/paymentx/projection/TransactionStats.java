package com.pratham.paymentx.projection;

import java.math.BigDecimal;

public interface TransactionStats {
    Long getTxCount();
    BigDecimal getTotalVolume();
}