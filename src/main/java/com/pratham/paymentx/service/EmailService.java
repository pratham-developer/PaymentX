package com.pratham.paymentx.service;

public interface EmailService {
    void sendBankVerificationFailureEmail(String toEmail, String businessName);
    void sendPayoutFailureEmail(String toEmail, String businessName, String amount);
}
