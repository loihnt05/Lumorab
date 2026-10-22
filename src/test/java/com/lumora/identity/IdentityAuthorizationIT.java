package com.lumora.identity;

import static org.assertj.core.api.Assertions.*;

import com.lumora.identity.security.ClerkJwtConfiguration;
import com.lumora.support.TestTokens;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "app.clerk.issuer=https://lumora-test.clerk.accounts.dev",
      "app.clerk.authorized-parties=http://localhost:3000"
    })
@Import(IdentityAuthorizationIT.TestJwtConfiguration.class)
class IdentityAuthorizationIT {
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired JsonMapper json;
  private HttpClient client;
  private final Set<String> subjects = new HashSet<>();
  private final Set<UUID> properties = new HashSet<>();

  @TestConfiguration(proxyBeanMethods = false)
  static class TestJwtConfiguration {
    @Bean
    @Primary
    JwtDecoder testJwtDecoder() throws Exception {
      var decoder = NimbusJwtDecoder.withPublicKey(TestTokens.KEY.toRSAPublicKey()).build();
      decoder.setJwtValidator(ClerkJwtConfiguration.validators(TestTokens.PROPERTIES));
      return decoder;
    }
  }

  @BeforeEach
  void setUp() {
    client = HttpClient.newHttpClient();
  }

  @AfterEach
  void cleanUp() {
    client.close();
    // Delete only the unique fixture identities/properties created by this test, never other data.
    subjects.forEach(subject -> jdbc.update("DELETE FROM users WHERE clerk_user_id = ?", subject));
    properties.forEach(id -> jdbc.update("DELETE FROM properties WHERE id = ?", id));
  }

  @Test
  void anonymousAndInvalidTokensAreUnauthorizedAndDoNotCreateUsers() throws Exception {
    String subject = subject();
    assertError(send("GET", "/api/me", null), 401, "UNAUTHORIZED");
    assertError(send("GET", "/api/me", "garbage"), 401, "UNAUTHORIZED");
    assertError(
        send("GET", "/api/me", TestTokens.token(subject, c -> c.issuer("https://wrong.example"))),
        401,
        "UNAUTHORIZED");
    assertError(
        send("GET", "/api/me", TestTokens.token(subject, c -> {}, TestTokens.key())),
        401,
        "UNAUTHORIZED");
    assertError(
        send(
            "GET",
            "/api/me",
            TestTokens.token(
                subject,
                c ->
                    c.issueTime(Date.from(Instant.now().minusSeconds(600)))
                        .expirationTime(Date.from(Instant.now().minusSeconds(180))))),
        401,
        "UNAUTHORIZED");
    assertError(
        send(
            "GET",
            "/api/me",
            TestTokens.token(subject, c -> c.claim("azp", "https://wrong.example"))),
        401,
        "UNAUTHORIZED");
    assertThat(count(subject)).isZero();
  }

  @Test
  void firstLoginCreatesOneUserAndNeverTrustsRoleClaims() throws Exception {
    String subject = subject();
    String token =
        TestTokens.token(
            subject,
            c ->
                c.claim("email", "guest@example.test")
                    .claim("first_name", "Guest")
                    .claim("role", "ADMIN")
                    .claim("metadata", Map.of("role", "ADMIN")));
    var first = send("GET", "/api/me", token);
    assertThat(first.statusCode()).isEqualTo(200);
    var body = json.readTree(first.body());
    assertThat(body.get("role").asText()).isEqualTo("USER");
    assertThat(body.get("email").asText()).isEqualTo("guest@example.test");
    var second = send("GET", "/api/me", token);
    assertThat(json.readTree(second.body()).get("id").asText()).isEqualTo(body.get("id").asText());
    assertThat(count(subject)).isEqualTo(1);
    assertError(
        send("GET", "/api/admin/users/" + body.get("id").asText(), token), 403, "FORBIDDEN");
  }

  @Test
  void serverIssuedSessionWithoutAzpSynchronizesLocalUser() throws Exception {
    String subject = subject();
    String token = TestTokens.token(subject, claims -> claims.claim("azp", null));
    var profile = json.readTree(send("GET", "/api/me", token).body());
    assertThat(profile.get("clerkUserId").asText()).isEqualTo(subject);
    assertThat(profile.get("role").asText()).isEqualTo("USER");
    assertThat(count(subject)).isEqualTo(1);
  }

  @Test
  void profileChangesKeepIdentityAndOldTokensCannotUndoNewProfileOrRoles() throws Exception {
    String subject = subject();
    Instant now = Instant.now();
    String old =
        TestTokens.token(
            subject,
            c ->
                c.issueTime(Date.from(now.minusSeconds(30)))
                    .claim("email", "old@example.test")
                    .claim("first_name", "Old"));
    String id = json.readTree(send("GET", "/api/me", old).body()).get("id").asText();
    jdbc.update("UPDATE users SET role = 'MANAGER' WHERE clerk_user_id = ?", subject);
    String fresh =
        TestTokens.token(
            subject,
            c -> c.issueTime(Date.from(now.minusSeconds(5))).claim("email", "new@example.test"));
    var updated = json.readTree(send("GET", "/api/me", fresh).body());
    assertThat(updated.get("id").asText()).isEqualTo(id);
    assertThat(updated.get("role").asText()).isEqualTo("MANAGER");
    assertThat(updated.get("email").asText()).isEqualTo("new@example.test");
    assertThat(updated.get("firstName").asText()).isEqualTo("Old");
    var replayed = json.readTree(send("GET", "/api/me", old).body());
    assertThat(replayed.get("email").asText()).isEqualTo("new@example.test");
    assertThat(count(subject)).isEqualTo(1);
  }

  @Test
  void sameEmailDoesNotMergeTwoClerkIdentities() throws Exception {
    String first = subject();
    String second = subject();
    send("GET", "/api/me", TestTokens.token(first, c -> c.claim("email", "same@example.test")));
    send("GET", "/api/me", TestTokens.token(second, c -> c.claim("email", "same@example.test")));
    assertThat(userId(first)).isNotEqualTo(userId(second));
  }

  @Test
  void concurrentFirstRequestsCreateExactlyOneUser() {
    String subject = subject();
    String token = TestTokens.token(subject);
    var requests = new ArrayList<CompletableFuture<HttpResponse<String>>>();
    for (int i = 0; i < 12; i++)
      requests.add(
          client.sendAsync(request("GET", "/api/me", token), HttpResponse.BodyHandlers.ofString()));
    var responses = requests.stream().map(CompletableFuture::join).toList();
    assertThat(responses).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(200));
    assertThat(count(subject)).isEqualTo(1);
  }

  @Test
  void rolesAndDisabledStatusAreReloadedOnEveryRequest() throws Exception {
    String subject = subject();
    String token = TestTokens.token(subject);
    send("GET", "/api/me", token);
    String adminPath = "/api/admin/users/" + userId(subject);
    assertError(send("GET", adminPath, token), 403, "FORBIDDEN");
    jdbc.update("UPDATE users SET role = 'MANAGER' WHERE clerk_user_id = ?", subject);
    assertError(send("GET", adminPath, token), 403, "FORBIDDEN");
    jdbc.update("UPDATE users SET role = 'ADMIN' WHERE clerk_user_id = ?", subject);
    assertThat(send("GET", adminPath, token).statusCode()).isEqualTo(200);
    jdbc.update("UPDATE users SET role = 'USER' WHERE clerk_user_id = ?", subject);
    assertError(send("GET", adminPath, token), 403, "FORBIDDEN");
    jdbc.update("UPDATE users SET status = 'DISABLED' WHERE clerk_user_id = ?", subject);
    assertError(send("GET", "/api/me", token), 403, "ACCOUNT_DISABLED");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM users WHERE clerk_user_id = ?", String.class, subject))
        .isEqualTo("DISABLED");
  }

  @Test
  void managerScopeAssignmentsAndRevocationAreEnforcedServerSide() throws Exception {
    String admin = actor(Role.ADMIN);
    String manager = actor(Role.MANAGER);
    String otherManager = actor(Role.MANAGER);
    String guest = actor(Role.USER);
    UUID a = property();
    UUID b = property();
    String managerToken = TestTokens.token(manager);
    String adminToken = TestTokens.token(admin);
    String assignment = "/api/admin/users/" + userId(manager) + "/properties/" + a;

    assertError(send("GET", "/api/manager/properties/" + a, managerToken), 403, "FORBIDDEN");
    assertError(send("PUT", assignment, managerToken), 403, "FORBIDDEN");
    assertError(send("PUT", assignment, TestTokens.token(guest)), 403, "FORBIDDEN");
    assertThat(send("PUT", assignment, adminToken).statusCode()).isEqualTo(204);
    assertThat(send("PUT", assignment, adminToken).statusCode()).isEqualTo(204);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM user_properties WHERE user_id = ? AND property_id = ?",
                Integer.class,
                userId(manager),
                a))
        .isEqualTo(1);
    assertThat(send("GET", "/api/manager/properties/" + a, managerToken).statusCode())
        .isEqualTo(200);
    assertError(send("GET", "/api/manager/properties/" + b, managerToken), 403, "FORBIDDEN");
    assertError(
        send("GET", "/api/manager/properties/" + a, TestTokens.token(otherManager)),
        403,
        "FORBIDDEN");
    assertError(
        send("GET", "/api/manager/properties/" + a, TestTokens.token(guest)), 403, "FORBIDDEN");
    assertThat(send("GET", "/api/manager/properties/" + b, adminToken).statusCode()).isEqualTo(200);
    JsonNode list = json.readTree(send("GET", "/api/manager/properties", managerToken).body());
    assertThat(list.size()).isEqualTo(1);
    assertThat(list.get(0).get("id").asText()).isEqualTo(a.toString());

    assertThat(
            send("PUT", "/api/admin/users/" + userId(manager) + "/properties/" + b, adminToken)
                .statusCode())
        .isEqualTo(204);
    assertThat(json.readTree(send("GET", "/api/manager/properties", managerToken).body()).size())
        .isEqualTo(2);
    assertThat(
            json.readTree(
                    send("GET", "/api/admin/users/" + userId(manager) + "/properties", adminToken)
                        .body())
                .size())
        .isEqualTo(2);
    assertThat(send("DELETE", assignment, adminToken).statusCode()).isEqualTo(204);
    assertThat(send("DELETE", assignment, adminToken).statusCode()).isEqualTo(204);
    assertError(send("GET", "/api/manager/properties/" + a, managerToken), 403, "FORBIDDEN");
    assertThat(send("GET", "/api/manager/properties/" + b, managerToken).statusCode())
        .isEqualTo(200);
  }

  @Test
  void rejectsAssignmentsToNonManagersAndUnknownResources() throws Exception {
    String adminToken = TestTokens.token(actor(Role.ADMIN));
    String guest = actor(Role.USER);
    String manager = actor(Role.MANAGER);
    UUID property = property();
    assertError(
        send("PUT", "/api/admin/users/" + userId(guest) + "/properties/" + property, adminToken),
        409,
        "CONFLICT");
    assertError(
        send(
            "PUT",
            "/api/admin/users/" + userId(manager) + "/properties/" + UUID.randomUUID(),
            adminToken),
        404,
        "NOT_FOUND");
    assertError(
        send(
            "PUT", "/api/admin/users/" + UUID.randomUUID() + "/properties/" + property, adminToken),
        404,
        "NOT_FOUND");
    assertError(send("GET", "/api/admin/users/not-a-uuid", adminToken), 400, "BAD_REQUEST");
    assertError(
        send("GET", "/api/manager/properties/" + UUID.randomUUID(), adminToken), 404, "NOT_FOUND");
  }

  @Test
  void databaseRejectsDanglingAndDuplicateAssignments() throws Exception {
    String manager = actor(Role.MANAGER);
    UUID id = property();
    jdbc.update(
        "INSERT INTO user_properties (id,user_id,property_id) VALUES (?,?,?)",
        UUID.randomUUID(),
        userId(manager),
        id);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO user_properties (id,user_id,property_id) VALUES (?,?,?)",
                    UUID.randomUUID(),
                    userId(manager),
                    id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO user_properties (id,user_id,property_id) VALUES (?,?,?)",
                    UUID.randomUUID(),
                    userId(manager),
                    UUID.randomUUID()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  private String subject() {
    String subject = "user_stage1_" + UUID.randomUUID().toString().replace("-", "");
    subjects.add(subject);
    return subject;
  }

  private String actor(Role role) throws Exception {
    String subject = subject();
    assertThat(send("GET", "/api/me", TestTokens.token(subject)).statusCode()).isEqualTo(200);
    jdbc.update("UPDATE users SET role = ? WHERE clerk_user_id = ?", role.name(), subject);
    return subject;
  }

  private UUID property() {
    UUID id = UUID.randomUUID();
    properties.add(id);
    jdbc.update(
        "INSERT INTO properties (id,name,slug) VALUES (?,?,?)",
        id,
        "Test property " + id,
        "test-" + id);
    return id;
  }

  private UUID userId(String subject) {
    return jdbc.queryForObject("SELECT id FROM users WHERE clerk_user_id = ?", UUID.class, subject);
  }

  private int count(String subject) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM users WHERE clerk_user_id = ?", Integer.class, subject);
  }

  private HttpRequest request(String method, String path, String token) {
    var builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(java.time.Duration.ofSeconds(20))
            .method(method, HttpRequest.BodyPublishers.noBody())
            .header("X-Request-Id", "stage1-integration");
    if (token != null) builder.header("Authorization", "Bearer " + token);
    return builder.build();
  }

  private HttpResponse<String> send(String method, String path, String token) throws Exception {
    return client.send(request(method, path, token), HttpResponse.BodyHandlers.ofString());
  }

  private void assertError(HttpResponse<String> response, int status, String code) {
    assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(status);
    var body = json.readTree(response.body());
    assertThat(body.get("code").asText()).isEqualTo(code);
    assertThat(body.get("details").isObject()).isTrue();
    assertThat(body.get("requestId").asText()).isEqualTo("stage1-integration");
    assertThat(response.headers().firstValue("X-Request-Id")).contains("stage1-integration");
  }
}
