package com.lumora.common.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(Boolean enabled, Integer requestsPerMinute) {
  public RateLimitProperties {
    enabled = enabled == null || enabled;
    requestsPerMinute =
        requestsPerMinute == null || requestsPerMinute <= 0 ? 600 : requestsPerMinute;
  }
}
