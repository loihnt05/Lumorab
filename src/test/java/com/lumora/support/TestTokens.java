package com.lumora.support;

import com.lumora.identity.security.ClerkProperties;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

// Ephemeral signing material exists ONLY in tests; never a runtime authentication fallback.
public final class TestTokens {
  public static final String ISSUER = "https://lumora-test.clerk.accounts.dev";
  public static final String ORIGIN = "http://localhost:3000";
  public static final ClerkProperties PROPERTIES = new ClerkProperties(ISSUER, List.of(ORIGIN), "");
  public static final RSAKey KEY = key();

  private TestTokens() {}

  public static RSAKey key() {
    try {
      return new RSAKeyGenerator(2048).keyID("test-signing-key").generate();
    } catch (JOSEException exception) {
      throw new IllegalStateException(exception);
    }
  }

  public static String token(String subject) {
    return token(subject, claims -> {});
  }

  public static String token(String subject, Consumer<JWTClaimsSet.Builder> customize) {
    return token(subject, customize, KEY);
  }

  public static String token(String subject, Consumer<JWTClaimsSet.Builder> customize, RSAKey key) {
    Instant now = Instant.now();
    var claims =
        new JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .subject(subject)
            .issueTime(Date.from(now.minusSeconds(10)))
            .notBeforeTime(Date.from(now.minusSeconds(10)))
            .expirationTime(Date.from(now.plusSeconds(120)))
            .claim("sid", "sess_test")
            .claim("azp", ORIGIN)
            .claim("v", 2);
    customize.accept(claims);
    var jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
            claims.build());
    try {
      jwt.sign(new RSASSASigner(key));
      return jwt.serialize();
    } catch (JOSEException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
