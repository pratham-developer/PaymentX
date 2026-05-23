package com.pratham.paymentx.config.brevo;

import brevo.ApiClient;
import brevo.auth.ApiKeyAuth;
import brevoApi.TransactionalEmailsApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BrevoProperties.class)
public class BrevoConfig {

    private final BrevoProperties properties;

    public BrevoConfig(BrevoProperties properties) {
        this.properties = properties;
    }

    @Bean
    public TransactionalEmailsApi transactionalEmailsApi() {
        ApiClient defaultClient = brevo.Configuration.getDefaultApiClient();

        ApiKeyAuth apiKey =
                (ApiKeyAuth) defaultClient.getAuthentication("api-key");

        apiKey.setApiKey(properties.apiKey());

        return new TransactionalEmailsApi();
    }
}