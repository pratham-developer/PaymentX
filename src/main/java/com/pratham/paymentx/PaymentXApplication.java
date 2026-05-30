package com.pratham.paymentx;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.TimeZone;

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

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        SpringApplication.run(PaymentXApplication.class, args);
    }



}
