package com.lumora.identity.security;

import java.time.Instant;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;

public final class ClerkSessionValidator implements OAuth2TokenValidator<Jwt> {
  private final ClerkProperties properties;

  public ClerkSessionValidator(ClerkProperties properties) {
    this.properties = properties;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    if (!identifier(jwt.getClaims().get("sub"), "user_")
        || !identifier(jwt.getClaims().get("sid"), "sess_")
        || jwt.getExpiresAt() == null
        || jwt.getNotBefore() == null
        || jwt.getIssuedAt() == null
        || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
        || jwt.getIssuedAt().isAfter(Instant.now().plusSeconds(60))) return invalid();
    Object party = jwt.getClaims().get("azp");
    if (!(party instanceof String) || !properties.authorizedParties().contains(party))
      return invalid();
    Object sessionStatus = jwt.getClaims().get("sts");
    if (sessionStatus != null && !"active".equals(sessionStatus)) return invalid();
    if (properties.audience() != null
        && !properties.audience().isBlank()
        && (jwt.getAudience() == null || !jwt.getAudience().contains(properties.audience())))
      return invalid();
    if (!profileClaim(jwt, "email", 320)
        || !profileClaim(jwt, "first_name", 255)
        || !profileClaim(jwt, "last_name", 255)) return invalid();
    return OAuth2TokenValidatorResult.success();
  }

  private boolean identifier(Object value, String prefix) {
    return value instanceof String text
        && text.startsWith(prefix)
        && text.length() > prefix.length()
        && text.length() <= 255
        && text.matches("[A-Za-z0-9_-]+");
  }

  private boolean profileClaim(Jwt jwt, String name, int maxLength) {
    Object value = jwt.getClaims().get(name);
    return value == null || (value instanceof String text && text.length() <= maxLength);
  }

  private OAuth2TokenValidatorResult invalid() {
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error("invalid_token", "Invalid Clerk session claims.", null));
  }
}
