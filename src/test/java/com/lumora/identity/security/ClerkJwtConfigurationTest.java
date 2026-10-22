package com.lumora.identity.security;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.lumora.support.TestTokens;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class ClerkJwtConfigurationTest {
  private NimbusJwtDecoder decoder;

  @BeforeEach
  void setUp() {
    var http = new RestTemplate();
    var server = MockRestServiceServer.createServer(http);
    server
        .expect(ExpectedCount.manyTimes(), requestTo(TestTokens.ISSUER + "/.well-known/jwks.json"))
        .andRespond(
            withSuccess(
                new JWKSet(TestTokens.KEY.toPublicJWK()).toString(), MediaType.APPLICATION_JSON));
    decoder = ClerkJwtConfiguration.configuredDecoder(TestTokens.PROPERTIES, http);
  }

  @Test
  void verifiesSignatureThroughConfiguredJwksAndAcceptsStandardClerkClaims() {
    assertThat(decoder.decode(TestTokens.token("user_valid")).getSubject()).isEqualTo("user_valid");
  }

  @Test
  void acceptsServerIssuedSessionWithoutAzpButRejectsUntrustedAzp() {
    assertThat(
            decoder
                .decode(TestTokens.token("user_server", claims -> claims.claim("azp", null)))
                .getSubject())
        .isEqualTo("user_server");
    assertThatThrownBy(
            () ->
                decoder.decode(
                    TestTokens.token(
                        "user_foreign",
                        claims -> claims.claim("azp", "https://untrusted.example"))))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsForgedSignatureEvenWithMatchingKeyId() {
    assertThatThrownBy(
            () -> decoder.decode(TestTokens.token("user_forged", claims -> {}, TestTokens.key())))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsOtherSigningAlgorithmsEvenWithTheTrustedRsaKey() throws Exception {
    var claims = SignedJWT.parse(TestTokens.token("user_algorithm")).getJWTClaimsSet();
    var signed =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS512).keyID(TestTokens.KEY.getKeyID()).build(),
            claims);
    signed.sign(new RSASSASigner(TestTokens.KEY));
    assertThatThrownBy(() -> decoder.decode(signed.serialize())).isInstanceOf(JwtException.class);
  }

  @Test
  void refreshesJwksOnSigningKeyRotation() {
    var rotated = new RSAKey.Builder(TestTokens.key()).keyID("rotated-test-key").build();
    var http = new RestTemplate();
    var server = MockRestServiceServer.createServer(http);
    server
        .expect(requestTo(TestTokens.ISSUER + "/.well-known/jwks.json"))
        .andRespond(
            withSuccess(
                new JWKSet(TestTokens.KEY.toPublicJWK()).toString(), MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(TestTokens.ISSUER + "/.well-known/jwks.json"))
        .andRespond(
            withSuccess(new JWKSet(rotated.toPublicJWK()).toString(), MediaType.APPLICATION_JSON));
    var rotatingDecoder = ClerkJwtConfiguration.configuredDecoder(TestTokens.PROPERTIES, http);
    rotatingDecoder.decode(TestTokens.token("user_rotation"));
    assertThat(
            rotatingDecoder
                .decode(TestTokens.token("user_rotation", claims -> {}, rotated))
                .getSubject())
        .isEqualTo("user_rotation");
    server.verify();
  }

  @Test
  void rejectsMalformedAndUnsignedTokens() {
    assertThatThrownBy(() -> decoder.decode("not-a-jwt")).isInstanceOf(JwtException.class);
    assertThatThrownBy(() -> decoder.decode("eyJhbGciOiJub25lIn0.eyJzdWIiOiJ1c2VyX2JhZCJ9."))
        .isInstanceOf(JwtException.class);
  }

  static Stream<Consumer<JWTClaimsSet.Builder>> invalidClaims() {
    return Stream.of(
        claims -> claims.issuer("https://another.clerk.accounts.dev"),
        claims ->
            claims
                .issueTime(Date.from(Instant.now().minusSeconds(600)))
                .expirationTime(Date.from(Instant.now().minusSeconds(180))),
        claims -> claims.notBeforeTime(Date.from(Instant.now().plusSeconds(180))),
        claims -> claims.issueTime(Date.from(Instant.now().plusSeconds(180))),
        claims -> claims.claim("exp", null),
        claims -> claims.claim("nbf", null),
        claims -> claims.claim("iat", null),
        claims -> claims.claim("sub", ""),
        claims -> claims.claim("sub", "machine_user"),
        claims -> claims.claim("sid", null),
        claims -> claims.claim("azp", "http://untrusted.example"),
        claims -> claims.claim("sts", "pending"),
        claims -> claims.claim("email", Map.of("unexpected", "object")),
        claims -> claims.claim("first_name", "x".repeat(256)));
  }

  @ParameterizedTest
  @MethodSource("invalidClaims")
  void rejectsInvalidSessionClaims(Consumer<JWTClaimsSet.Builder> customize) {
    assertThatThrownBy(() -> decoder.decode(TestTokens.token("user_invalid", customize)))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void enforcesAudienceOnlyWhenConfigured() {
    decoder.setJwtValidator(
        ClerkJwtConfiguration.validators(
            new ClerkProperties(TestTokens.ISSUER, List.of(TestTokens.ORIGIN), "lumorab")));
    assertThatThrownBy(() -> decoder.decode(TestTokens.token("user_audience")))
        .isInstanceOf(JwtException.class);
    assertThat(
            decoder
                .decode(TestTokens.token("user_audience", claims -> claims.audience("lumorab")))
                .getSubject())
        .isEqualTo("user_audience");
  }

  @Test
  void unconfiguredBackendNeverAcceptsAToken() {
    var unconfigured =
        new ClerkJwtConfiguration()
            .jwtDecoder(new ClerkProperties("", List.of(TestTokens.ORIGIN), ""));
    assertThatThrownBy(() -> unconfigured.decode(TestTokens.token("user_any")))
        .isInstanceOf(BadJwtException.class);
  }

  @Test
  void rejectsInsecureIssuerAndWildcardAuthorizedParty() {
    assertThatThrownBy(
            () ->
                ClerkJwtConfiguration.configuredDecoder(
                    new ClerkProperties("http://issuer.example", List.of(TestTokens.ORIGIN), ""),
                    new RestTemplate()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                ClerkJwtConfiguration.configuredDecoder(
                    new ClerkProperties(TestTokens.ISSUER, List.of("*"), ""), new RestTemplate()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
