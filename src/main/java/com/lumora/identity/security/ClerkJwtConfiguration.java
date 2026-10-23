package com.lumora.identity.security;

import java.net.URI;
import java.time.Duration;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(ClerkProperties.class)
public class ClerkJwtConfiguration {
  @Bean
  JwtDecoder jwtDecoder(ClerkProperties properties, LocalJwtService localJwtService) {
    JwtDecoder clerkDecoder;
    if (properties.issuer() == null || properties.issuer().isBlank()) {
      LoggerFactory.getLogger(getClass())
          .warn(
              "CLERK_ISSUER is not configured; all external clerk bearer tokens will be rejected.");
      clerkDecoder =
          token -> {
            throw new BadJwtException("Clerk is not configured.");
          };
    } else {
      var requestFactory = new SimpleClientHttpRequestFactory();
      requestFactory.setConnectTimeout(Duration.ofSeconds(3));
      requestFactory.setReadTimeout(Duration.ofSeconds(3));
      clerkDecoder = configuredDecoder(properties, new RestTemplate(requestFactory));
    }

    return token -> {
      if (localJwtService != null && localJwtService.isLocalToken(token)) {
        return localJwtService.decode(token);
      }
      return clerkDecoder.decode(token);
    };
  }

  public JwtDecoder jwtDecoder(ClerkProperties properties) {
    return jwtDecoder(properties, new LocalJwtService(""));
  }

  public static NimbusJwtDecoder configuredDecoder(
      ClerkProperties properties, RestOperations http) {
    URI issuer = URI.create(properties.issuer());
    if (!"https".equals(issuer.getScheme())
        || issuer.getHost() == null
        || issuer.getUserInfo() != null
        || issuer.getQuery() != null
        || issuer.getFragment() != null
        || properties.issuer().endsWith("/")) {
      throw new IllegalArgumentException(
          "CLERK_ISSUER must be an HTTPS issuer URL without credentials, query, fragment or trailing slash.");
    }
    if (properties.authorizedParties() == null
        || properties.authorizedParties().isEmpty()
        || properties.authorizedParties().stream()
            .anyMatch(value -> value.isBlank() || value.equals("*"))) {
      throw new IllegalArgumentException(
          "CLERK_AUTHORIZED_PARTIES must contain exact permitted origins.");
    }
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withJwkSetUri(properties.issuer() + "/.well-known/jwks.json")
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .restOperations(http)
            .build();
    decoder.setJwtValidator(validators(properties));
    return decoder;
  }

  public static OAuth2TokenValidator<Jwt> validators(ClerkProperties properties) {
    return new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(properties.issuer()),
        new ClerkSessionValidator(properties));
  }
}
