package com.pratham.paymentx.config.cashfree;

import com.cashfree.CashfreePayout;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@EnableConfigurationProperties(CashfreeProperties.class)
public class CashfreeConfig {

    private final CashfreeProperties properties;

    public CashfreeConfig(CashfreeProperties properties) {
        this.properties = properties;
    }

    @Bean
    public CashfreePayout cashfreePayout() {
        CashfreePayout.XClientId = properties.clientId();
        CashfreePayout.XClientSecret = properties.clientSecret();
        CashfreePayout.XEnvironment = properties.environment().equalsIgnoreCase("PRODUCTION")
                ? CashfreePayout.PRODUCTION
                : CashfreePayout.SANDBOX;
        return new CashfreePayout();
    }
}