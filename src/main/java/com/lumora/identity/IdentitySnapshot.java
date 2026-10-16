package com.lumora.identity;

import java.time.Instant;
import org.springframework.security.oauth2.jwt.Jwt;

// Profile data from an already-verified token. Business role/status never enter this snapshot.
public record IdentitySnapshot(
    String clerkUserId,
    Instant issuedAt,
    ProfileClaim email,
    ProfileClaim firstName,
    ProfileClaim lastName) {
  public record ProfileClaim(boolean supplied, String value) {}

  public static IdentitySnapshot fromVerifiedJwt(Jwt jwt) {
    return new IdentitySnapshot(
        jwt.getSubject(),
        jwt.getIssuedAt(),
        claim(jwt, "email"),
        claim(jwt, "first_name"),
        claim(jwt, "last_name"));
  }

  private static ProfileClaim claim(Jwt jwt, String name) {
    return new ProfileClaim(jwt.getClaims().containsKey(name), jwt.getClaimAsString(name));
  }
}
