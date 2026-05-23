package com.pratham.paymentx.config.brevo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "brevo")
public record BrevoProperties(
        String apiKey,
        String senderEmail,
        String senderName,
        String dashboardUrl   // injected into email CTAs so it's env-configurable
) {}