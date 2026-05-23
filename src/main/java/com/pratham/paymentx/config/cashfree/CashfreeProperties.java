package com.pratham.paymentx.config.cashfree;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cashfree")
public record CashfreeProperties(
        String clientId,
        String clientSecret,
        String environment,
        Payout payout
) {
    public record Payout(
            String webhookSecret,
            java.util.List<String> allowedIps
    ) {}
}