package com.pratham.paymentx.service.impl;

import brevoApi.TransactionalEmailsApi;
import brevoModel.SendSmtpEmail;
import brevoModel.SendSmtpEmailSender;
import brevoModel.SendSmtpEmailTo;
import com.pratham.paymentx.config.brevo.BrevoProperties;
import com.pratham.paymentx.service.EmailService;
import com.pratham.paymentx.util.EmailTemplateLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final TransactionalEmailsApi transactionalEmailsApi;
    private final BrevoProperties brevoProperties;
    private final EmailTemplateLoader templateLoader;

    /**
     * Notifies the merchant that their bank details failed Cashfree verification
     * during admin approval. Profile has been reset — they must re-enter correct
     * bank details on next dashboard visit.
     */
    @Override
    @Async
    public void sendBankVerificationFailureEmail(String toEmail, String businessName) {
        String html = templateLoader.load(
                "bank-verification-failure.html",
                Map.of(
                        "BUSINESS_NAME", businessName,
                        "DASHBOARD_URL", brevoProperties.dashboardUrl()
                )
        );
        sendEmail(toEmail, businessName,
                "Action Required: Bank Details Could Not Be Verified — PaymentX", html);
    }

    /**
     * Notifies the merchant that a scheduled payout failed or was reversed.
     * Wallet funds have been restored; account is quarantined until re-onboarded.
     */
    @Override
    @Async
    public void sendPayoutFailureEmail(String toEmail, String businessName, String amount) {
        String html = templateLoader.load(
                "payout-failure.html",
                Map.of(
                        "BUSINESS_NAME", businessName,
                        "AMOUNT", amount,
                        "DASHBOARD_URL", brevoProperties.dashboardUrl()
                )
        );
        sendEmail(toEmail, businessName,
                "Payout Failed — Action Required — PaymentX", html);
    }

    // ─── Internal ────────────────────────────────────────────────────────────

    private void sendEmail(String toEmail, String toName, String subject, String htmlContent) {
        try {
            SendSmtpEmailSender sender = new SendSmtpEmailSender();
            sender.setEmail(brevoProperties.senderEmail());
            sender.setName(brevoProperties.senderName());

            SendSmtpEmailTo recipient = new SendSmtpEmailTo();
            recipient.setEmail(toEmail);
            recipient.setName(toName);

            SendSmtpEmail email = new SendSmtpEmail();
            email.setSender(sender);
            email.setTo(List.of(recipient));
            email.setSubject(subject);
            email.setHtmlContent(htmlContent);

            transactionalEmailsApi.sendTransacEmail(email);
            log.info("Email sent to {} | subject: {}", toEmail, subject);

        } catch (Exception e) {
            log.error("Failed to send email to {} | subject: {} | error: {}",
                    toEmail, subject, e.getMessage());
        }
    }
}