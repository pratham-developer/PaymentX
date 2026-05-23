package com.pratham.paymentx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@ConfigurationPropertiesScan
@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
/*
  Disables Spring Boot's UserDetailsService autoconfiguration.
  By default, Spring Boot creates an InMemoryUserDetailsManager with a generated
  username ("user") and random password if no UserDetailsService bean is defined.
  Our system uses passwordless authentication (Google ID token + JWT),
  so we do not require the default in-memory user or UserDetailsService.
 */
@EnableAsync
public class PaymentXApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentXApplication.class, args);
    }

}
