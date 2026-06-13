package com.pratham.paymentx.dto.dashboard;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class BalanceDto {
    private BigDecimal availableBalance;
    private BigDecimal processingBalance;
    private BigDecimal totalBalance;
}