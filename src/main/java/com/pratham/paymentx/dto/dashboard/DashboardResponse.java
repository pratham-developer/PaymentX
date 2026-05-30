package com.pratham.paymentx.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pratham.paymentx.enums.MerchantGatewayStatus;
import com.pratham.paymentx.enums.Role;
import com.pratham.paymentx.enums.WalletStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardResponse {
    // State Flags
    private Role role;
    private Boolean profileCompleted;
    private Boolean profileActive;
    private Boolean walletCreated;
    private WalletStatus walletStatus;
    private Boolean cardActive; // Only for STUDENT

    //Account Details
    private String name; // Unifies fullName (Student) and businessName (Merchant)
    private String collegeRegNo; // Only for STUDENT
    private String phone;

    // Financials
    private BigDecimal availableBalance;
    private BigDecimal processingBalance;
    private BigDecimal totalBalance;

    // Merchant Specific
    private MerchantGatewayStatus gatewayStatus;

    // TODO: private List<TransactionDto> recentTransactions;
}