package com.lumora.identity.security;

import com.lumora.identity.LocalUser;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

@Service
public class LocalJwtService {
  public static final String ISSUER = "lumora-auth";
  private static final String DEFAULT_SECRET =
      "lumora-super-secret-jwt-token-signing-key-minimum-256-bits-length-2026!";

  private final SecretKey secretKey;
  private final NimbusJwtDecoder decoder;

  public LocalJwtService(@Value("${lumora.auth.jwt-secret:" + DEFAULT_SECRET + "}") String secret) {
    byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
    // Ensure at least 32 bytes for HMAC-SHA256
    if (keyBytes.length < 32) {
      byte[] padded = new byte[32];
      System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
      keyBytes = padded;
    }
    this.secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
    this.decoder = NimbusJwtDecoder.withSecretKey(this.secretKey).build();
  }

  public boolean isLocalToken(String token) {
    if (token == null || token.isBlank()) {
      return false;
    }
    try {
      SignedJWT parsed = SignedJWT.parse(token);
      return ISSUER.equals(parsed.getJWTClaimsSet().getIssuer());
    } catch (Exception e) {
      return false;
    }
  }

  public Jwt decode(String token) {
    return this.decoder.decode(token);
  }

  public String generateToken(LocalUser user) {
    try {
      JWSSigner signer = new MACSigner(this.secretKey);
      Instant now = Instant.now();
      Date issuedAt = Date.from(now);
      Date expiresAt = Date.from(now.plus(7, ChronoUnit.DAYS));

      JWTClaimsSet claimsSet =
          new JWTClaimsSet.Builder()
              .subject(user.getClerkUserId())
              .issuer(ISSUER)
              .claim("email", user.getEmail() != null ? user.getEmail() : "")
              .claim("first_name", user.getFirstName() != null ? user.getFirstName() : "")
              .claim("last_name", user.getLastName() != null ? user.getLastName() : "")
              .claim("role", user.getRole().name())
              .issueTime(issuedAt)
              .expirationTime(expiresAt)
              .build();

      SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
      signedJWT.sign(signer);
      return signedJWT.serialize();
    } catch (Exception e) {
      throw new RuntimeException("Could not generate local JWT authentication token", e);
    }
  }
}
