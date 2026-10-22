package com.lumora.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PaymentProperties.class)
public class PaymentConfiguration {}

@ConfigurationProperties(prefix = "app.payment")
record PaymentProperties(Boolean mockEnabled) {
  PaymentProperties {
    mockEnabled = mockEnabled == null || mockEnabled;
  }
}
