package com.lumora.identity.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.clerk")
public record ClerkProperties(String issuer, List<String> authorizedParties, String audience) {}
