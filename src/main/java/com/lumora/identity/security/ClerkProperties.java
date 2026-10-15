package com.lumora.identity.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.clerk")
public record ClerkProperties(
    /** Exact HTTPS issuer URL of the Clerk application. */
    String issuer,
    /** Exact frontend origins accepted from the token's authorized-party claim. */
    List<String> authorizedParties,
    /** Optional audience required in Clerk session tokens. */
    String audience) {}
