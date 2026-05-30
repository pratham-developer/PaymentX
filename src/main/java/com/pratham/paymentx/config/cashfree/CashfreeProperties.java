package com.pratham.paymentx.config.cashfree;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cashfree")
public record CashfreeProperties(
        String environment,
        Payout payout,
        PaymentGateway paymentGateway
) {

    public record Payout(
            String clientId,
            String clientSecret
    ) {}

    public record PaymentGateway(
            String clientId,
            String clientSecret
    ) {}
}