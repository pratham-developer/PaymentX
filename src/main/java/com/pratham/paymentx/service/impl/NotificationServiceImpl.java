package com.pratham.paymentx.service.impl;

import com.pratham.paymentx.config.brevo.BrevoProperties;
import com.pratham.paymentx.messaging.event.EmailNotificationEvent;
import com.pratham.paymentx.messaging.publisher.EmailPublisher;
import com.pratham.paymentx.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final EmailPublisher emailPublisher;
    private final BrevoProperties brevoProperties;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    // CORE OPERATIONS

    @Override
    public void sendBankVerificationFailure(String toEmail, String businessName) {
        publishEvent(
                toEmail,
                businessName,
                "Action Required: Bank Details Could Not Be Verified — PaymentX",
                "bank-verification-failure.html",
                Map.of(
                        "BUSINESS_NAME", businessName,
                        "DASHBOARD_URL", brevoProperties.dashboardUrl()
                )
        );
    }

    @Override
    public void sendPayoutFailure(String toEmail, String businessName, BigDecimal amount) {
        publishEvent(
                toEmail,
                businessName,
                "Payout Failed — Action Required — PaymentX",
                "payout-failure.html",
                Map.of(
                        "BUSINESS_NAME", businessName,
                        "AMOUNT", amount.toString(),
                        "DASHBOARD_URL", brevoProperties.dashboardUrl()
                )
        );
    }

    @Override
    public void sendTopupSuccess(String toEmail, String userName, BigDecimal amount) {
        publishEvent(
                toEmail,
                userName,
                "Wallet Top-up Successful — PaymentX",
                "topup-success.html",
                Map.of(
                        "USER_NAME", userName,
                        "AMOUNT", amount.toString(),
                        "DASHBOARD_URL", brevoProperties.dashboardUrl()
                )
        );
    }

    // FINANCIAL REPORTING

    @Override
    public void sendDailyMerchantStatement(String toEmail, String businessName, LocalDate date,
                                           BigDecimal openingBalance, BigDecimal inflows,
                                           BigDecimal outflows, BigDecimal fees) {

        String formattedDate = date.format(DATE_FORMATTER);
        publishEvent(
                toEmail,
                businessName,
                "Your Daily Settlement Statement: " + formattedDate,
                "merchant-daily-statement.html",
                Map.of(
                        "BUSINESS_NAME", businessName,
                        "STATEMENT_DATE", formattedDate,
                        "OPENING_BALANCE", openingBalance.toString(),
                        "TOTAL_INFLOWS", inflows.toString(),
                        "TOTAL_OUTFLOWS", outflows.toString(),
                        "TOTAL_FEES", fees.toString(),
                        "DASHBOARD_URL", brevoProperties.dashboardUrl()
                )
        );
    }

    @Override
    public void sendAdminEodReport(LocalDate date, int totalTopupsCount, BigDecimal totalTopupVolume,
                                   int totalPayoutsCount, BigDecimal totalPayoutVolume,
                                   BigDecimal platformRevenue, BigDecimal totalProcessingEscrow) {

        String formattedDate = date.format(DATE_FORMATTER);
        publishEvent(
                brevoProperties.adminEmail(), // Send to your internal admin/founder email
                "PaymentX Founders",
                "EOD Financial Report — " + formattedDate,
                "admin-eod-report.html.html",
                Map.of(
                        "REPORT_DATE", formattedDate,
                        "TOPUP_COUNT", String.valueOf(totalTopupsCount),
                        "TOPUP_VOLUME", totalTopupVolume.toString(),
                        "PAYOUT_COUNT", String.valueOf(totalPayoutsCount),
                        "PAYOUT_VOLUME", totalPayoutVolume.toString(),
                        "PLATFORM_REVENUE", platformRevenue.toString(),
                        "ESCROW_LIABILITY", totalProcessingEscrow.toString()
                )
        );
    }

    @Override
    public void sendNfcPurchaseReceiptStudent(String toEmail, String studentName, String merchantName, BigDecimal amount) {
        publishEvent(toEmail, studentName, "Payment Receipt: " + merchantName, "nfc-receipt-student.html",
                Map.of("STUDENT_NAME", studentName, "MERCHANT_NAME", merchantName, "AMOUNT", amount.toString(), "DASHBOARD_URL", brevoProperties.dashboardUrl()));
    }

    @Override
    public void sendNfcPurchaseReceiptMerchant(String toEmail, String merchantName, String studentName, BigDecimal amount) {
        publishEvent(toEmail, merchantName, "Payment Received from " + studentName, "nfc-receipt-merchant.html",
                Map.of("MERCHANT_NAME", merchantName, "STUDENT_NAME", studentName, "AMOUNT", amount.toString(), "DASHBOARD_URL", brevoProperties.dashboardUrl()));
    }

    // HELPER
    private void publishEvent(String toEmail, String toName, String subject, String templateName, Map<String, String> placeholders) {
        EmailNotificationEvent event = new EmailNotificationEvent(
                toEmail,
                toName,
                subject,
                templateName,
                placeholders
        );
        emailPublisher.publish(event);
        log.debug("Enqueued email notification for {}: {}", toEmail, subject);
    }
}