package com.pratham.paymentx.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface NotificationService {

    // Core Operational Notifications
    void sendBankVerificationFailure(String toEmail, String businessName);
    void sendPayoutFailure(String toEmail, String businessName, BigDecimal amount);
    void sendTopupSuccess(String toEmail, String userName, BigDecimal amount);

    // Financial Reporting Notifications
    void sendDailyMerchantStatement(String toEmail, String businessName, LocalDate date,
                                    BigDecimal openingBalance, BigDecimal inflows,
                                    BigDecimal outflows, BigDecimal fees);

    void sendAdminEodReport(LocalDate date, int totalTopupsCount, BigDecimal totalTopupVolume,
                            int totalPayoutsCount, BigDecimal totalPayoutVolume,
                            BigDecimal platformRevenue, BigDecimal totalProcessingEscrow);

    void sendNfcPurchaseReceiptStudent(String toEmail, String studentName, String merchantName, BigDecimal amount);
    void sendNfcPurchaseReceiptMerchant(String toEmail, String merchantName, String studentName, BigDecimal amount);
}