package com.pratham.paymentx.service.impl;

import brevoApi.TransactionalEmailsApi;
import brevoModel.SendSmtpEmail;
import brevoModel.SendSmtpEmailSender;
import brevoModel.SendSmtpEmailTo;
import com.pratham.paymentx.config.brevo.BrevoProperties;
import com.pratham.paymentx.messaging.event.EmailNotificationEvent;
import com.pratham.paymentx.service.EmailService;
import com.pratham.paymentx.util.EmailTemplateLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final TransactionalEmailsApi transactionalEmailsApi;
    private final BrevoProperties brevoProperties;
    private final EmailTemplateLoader templateLoader;

    @Override
    // NO @Async - RabbitMQ manages the thread execution now!
    public void processEmailEvent(EmailNotificationEvent event) {
        log.info("Processing email dispatch for: {}", event.toEmail());

        // 1. Load and compile HTML
        String htmlContent = templateLoader.load(event.templateName(), event.placeholders());

        // 2. Dispatch via Brevo
        try {
            SendSmtpEmailSender sender = new SendSmtpEmailSender();
            sender.setEmail(brevoProperties.senderEmail());
            sender.setName(brevoProperties.senderName());

            SendSmtpEmailTo recipient = new SendSmtpEmailTo();
            recipient.setEmail(event.toEmail());
            recipient.setName(event.toName());

            SendSmtpEmail email = new SendSmtpEmail();
            email.setSender(sender);
            email.setTo(List.of(recipient));
            email.setSubject(event.subject());
            email.setHtmlContent(htmlContent);

            transactionalEmailsApi.sendTransacEmail(email);
            log.info("Email successfully dispatched to {} | subject: {}", event.toEmail(), event.subject());

        } catch (Exception e) {
            log.error("Brevo API failed to send email to {} | error: {}", event.toEmail(), e.getMessage());
            throw new RuntimeException("Email dispatch failed", e); // Triggers RabbitMQ backoff & DLQ
        }
    }
}