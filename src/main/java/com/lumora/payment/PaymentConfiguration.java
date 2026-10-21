package com.lumora.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PaymentProperties.class)
public class PaymentConfiguration {}

@ConfigurationProperties(prefix = "app.payment")
record PaymentProperties(Boolean mockEnabled, String defaultProvider, String webhookSecret) {
  PaymentProperties {
    mockEnabled = mockEnabled == null || mockEnabled;
    defaultProvider =
        defaultProvider == null || defaultProvider.isBlank() ? "MOCK" : defaultProvider.trim();
    webhookSecret =
        webhookSecret == null || webhookSecret.isBlank()
            ? "lumora-stage7-webhook-secret-key"
            : webhookSecret;
  }
}
