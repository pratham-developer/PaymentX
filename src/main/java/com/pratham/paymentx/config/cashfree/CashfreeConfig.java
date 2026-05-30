package com.pratham.paymentx.config.cashfree;

import com.cashfree.CashfreePayout;
import com.cashfree.pg.Cashfree;
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

        CashfreePayout.XClientId =
                properties.payout().clientId();

        CashfreePayout.XClientSecret =
                properties.payout().clientSecret();

        CashfreePayout.XEnvironment =
                properties.environment().equalsIgnoreCase("PRODUCTION")
                        ? CashfreePayout.PRODUCTION
                        : CashfreePayout.SANDBOX;

        return new CashfreePayout();
    }

    @Bean
    public Cashfree cashfree() {

        return new Cashfree(
                properties.environment().equalsIgnoreCase("PRODUCTION")
                        ? Cashfree.PRODUCTION
                        : Cashfree.SANDBOX,

                properties.paymentGateway().clientId(),
                properties.paymentGateway().clientSecret(),

                null,
                null,
                null
        );
    }
}