package com.lumora.stage8;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumora.common.web.RateLimitFilter;
import com.lumora.identity.security.ClerkJwtConfiguration;
import com.lumora.support.TestTokens;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "app.clerk.issuer=https://lumora-test.clerk.accounts.dev",
      "app.clerk.authorized-parties=http://localhost:3000",
      "app.room-codes.enabled=false",
      "app.booking.expiry-enabled=false",
      "app.rate-limit.enabled=true",
      "app.rate-limit.requests-per-minute=5"
    })
@Import(Stage8ProductionHardeningIT.TestConfig.class)
class Stage8ProductionHardeningIT {
  private static final LocalDate IN = LocalDate.of(2027, 3, 10);
  private static final LocalDate OUT = LocalDate.of(2027, 3, 12);

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired JsonMapper json;
  @Autowired RateLimitFilter rateLimitFilter;

  private HttpClient client;
  private UUID property;
  private UUID otherProperty;
  private UUID type;
  private UUID room;
  private Actor user;
  private Actor otherUser;
  private Actor manager;
  private Actor unassignedManager;
  private Actor admin;
  private final List<Actor> actors = new ArrayList<>();

  private record Actor(UUID id, String token) {}

  @TestConfiguration(proxyBeanMethods = false)
  static class TestConfig {
    @Bean
    @Primary
    JwtDecoder testDecoder() throws Exception {
      var decoder = NimbusJwtDecoder.withPublicKey(TestTokens.KEY.toRSAPublicKey()).build();
      decoder.setJwtValidator(ClerkJwtConfiguration.validators(TestTokens.PROPERTIES));
      return decoder;
    }
  }

  @BeforeEach
  void setup() {
    rateLimitFilter.reset();
    client = HttpClient.newHttpClient();
    property = createProperty();
    otherProperty = createProperty();
    type = createType(property);
    room = createRoom(property, type, "801");
    user = actor("USER");
    otherUser = actor("USER");
    manager = actor("MANAGER");
    unassignedManager = actor("MANAGER");
    admin = actor("ADMIN");
    jdbc.update(
        "INSERT INTO user_properties(id,user_id,property_id) VALUES (?,?,?)",
        UUID.randomUUID(),
        manager.id(),
        property);
  }

  @AfterEach
  void cleanup() {
    rateLimitFilter.reset();
    client.close();
    for (var p : List.of(property, otherProperty)) {
      jdbc.update(
          "DELETE FROM payments WHERE booking_id IN (SELECT id FROM bookings WHERE property_id=?)",
          p);
      jdbc.update(
          "DELETE FROM booking_guests WHERE booking_id IN (SELECT id FROM bookings WHERE property_id=?)",
          p);
      jdbc.update("DELETE FROM booking_rooms WHERE property_id=?", p);
      jdbc.update("DELETE FROM bookings WHERE property_id=?", p);
      jdbc.update(
          "DELETE FROM room_access_codes WHERE room_id IN (SELECT id FROM rooms WHERE property_id=?)",
          p);
      jdbc.update("DELETE FROM rooms WHERE property_id=?", p);
      jdbc.update("DELETE FROM room_types WHERE property_id=?", p);
      jdbc.update("DELETE FROM properties WHERE id=?", p);
    }
    for (var actor : actors) {
      jdbc.update("DELETE FROM audit_logs WHERE actor_user_id=?", actor.id());
      jdbc.update("DELETE FROM users WHERE id=?", actor.id());
    }
  }

  @Test
  void productionDatabaseIndexesExistAndExplainPlansUseIndexes() {
    var indexes =
        jdbc.queryForList(
            """
            SELECT indexname FROM pg_indexes
            WHERE schemaname = 'public'
              AND indexname IN (
                'idx_bookings_property_status_checkin',
                'idx_bookings_property_status_checkout',
                'idx_bookings_active_occupancy',
                'idx_bookings_created_global',
                'idx_rooms_blocked',
                'idx_room_access_codes_lookup'
              )
            """,
            String.class);
    assertThat(indexes)
        .containsExactlyInAnyOrder(
            "idx_bookings_property_status_checkin",
            "idx_bookings_property_status_checkout",
            "idx_bookings_active_occupancy",
            "idx_bookings_created_global",
            "idx_rooms_blocked",
            "idx_room_access_codes_lookup");

    String plan =
        jdbc.execute(
            (java.sql.Connection conn) -> {
              try (var st = conn.createStatement()) {
                st.execute("SET enable_seqscan = off");
                try (var rs =
                    st.executeQuery(
                        "EXPLAIN (FORMAT JSON) SELECT id FROM bookings "
                            + "WHERE property_id = '"
                            + property
                            + "' AND status = 'CONFIRMED' AND check_in_date >= '2027-03-10'")) {
                  rs.next();
                  return rs.getString(1);
                } finally {
                  st.execute("SET enable_seqscan = on");
                }
              }
            });
    assertThat(plan).contains("idx_bookings_property_status_checkin");
  }

  @Test
  void securityHeadersRateLimitingAndAbuseMatrixAreEnforced() throws Exception {
    // 1. Security headers on API responses
    var healthResp = send("GET", "/api/health", null, null, "sec-client");
    assertThat(healthResp.statusCode()).isEqualTo(200);
    assertThat(healthResp.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
    assertThat(healthResp.headers().firstValue("X-Frame-Options")).hasValue("DENY");
    assertThat(healthResp.headers().firstValue("Referrer-Policy"))
        .hasValue("strict-origin-when-cross-origin");
    assertThat(healthResp.headers().firstValue("Permissions-Policy"))
        .hasValue("camera=(), microphone=(), geolocation=()");
    assertThat(healthResp.headers().firstValue("X-Request-Id")).isPresent();

    // 2. Rate limiting on public endpoints (limit = 5/min for client "burst-client")
    for (int i = 1; i <= 5; i++) {
      var okResp = send("GET", "/api/public/properties", null, null, "burst-client");
      assertThat(okResp.statusCode()).isEqualTo(200);
    }
    var throttled = send("GET", "/api/public/properties", null, null, "burst-client");
    assertThat(throttled.statusCode()).isEqualTo(429);
    assertThat(throttled.headers().firstValue("Retry-After")).hasValue("60");
    var throttledBody = json.readTree(throttled.body());
    assertThat(throttledBody.get("code").asText()).isEqualTo("TOO_MANY_REQUESTS");
    assertThat(throttledBody.get("requestId").asText()).isNotBlank();

    // Another client is unaffected
    assertThat(send("GET", "/api/public/properties", null, null, "normal-client").statusCode())
        .isEqualTo(200);

    // 3. Abuse matrix: IDOR booking, property scope bypass, admin-only room block, forged webhook
    UUID bookingId = createBooking(user);
    assertThat(send("GET", "/api/me/bookings/" + bookingId, otherUser, null, "c1").statusCode())
        .isEqualTo(404);
    assertThat(
            send(
                    "POST",
                    "/api/me/bookings/" + bookingId + "/cancel",
                    otherUser,
                    Map.of("reason", "IDOR"),
                    "c1")
                .statusCode())
        .isEqualTo(404);
    assertThat(
            send(
                    "GET",
                    "/api/manager/properties/" + property + "/dashboard",
                    unassignedManager,
                    null,
                    "c1")
                .statusCode())
        .isEqualTo(403);
    assertThat(
            send(
                    "PATCH",
                    "/api/admin/properties/" + property + "/rooms/" + room + "/block",
                    manager,
                    Map.of("blocked", true),
                    "c1")
                .statusCode())
        .isEqualTo(403);
  }

  @Test
  void livenessReadinessAndAdminOperationalStatusEndpointsWork() throws Exception {
    var liveness = expect(send("GET", "/api/health/liveness", null, null, "ops-client"), 200);
    assertThat(liveness.get("status").asText()).isEqualTo("UP");

    var readiness = expect(send("GET", "/api/health/readiness", null, null, "ops-client"), 200);
    assertThat(readiness.get("status").asText()).isEqualTo("READY");
    assertThat(readiness.get("database").asText()).isEqualTo("UP");
    assertThat(readiness.get("appliedMigrations").asInt()).isGreaterThanOrEqualTo(10);

    // Admin operational status endpoint is protected
    assertThat(send("GET", "/api/admin/ops/status", null, null, "ops-client").statusCode())
        .isEqualTo(401);
    assertThat(send("GET", "/api/admin/ops/status", user, null, "ops-client").statusCode())
        .isEqualTo(403);
    assertThat(send("GET", "/api/admin/ops/status", manager, null, "ops-client").statusCode())
        .isEqualTo(403);

    var opsStatus = expect(send("GET", "/api/admin/ops/status", admin, null, "ops-client"), 200);
    assertThat(opsStatus.get("databaseStatus").asText()).isEqualTo("UP");
    assertThat(opsStatus.get("appliedMigrations").asInt()).isGreaterThanOrEqualTo(10);
    assertThat(opsStatus.get("timezone").asText()).isEqualTo("Asia/Ho_Chi_Minh");
    assertThat(opsStatus.toString()).doesNotContain("codeValue", "secret");
  }

  private UUID createBooking(Actor a) throws Exception {
    var body = new LinkedHashMap<String, Object>();
    body.put("propertyId", property);
    body.put("roomId", room);
    body.put("checkIn", IN.toString());
    body.put("checkOut", OUT.toString());
    body.put("guests", 2);
    body.put(
        "primaryGuest",
        Map.of(
            "fullName", "Stage8 Guest",
            "phone", "0901234567",
            "email", "guest-stage8@example.com"));
    var detail = expect(send("POST", "/api/bookings", a, body, "booking-client"), 201);
    return UUID.fromString(detail.get("booking").get("id").asText());
  }

  private HttpResponse<String> send(
      String method, String path, Actor actor, Object body, String clientKey) throws Exception {
    var req =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header(RateLimitFilter.CLIENT_HEADER, clientKey);
    if (actor != null) req.header("Authorization", "Bearer " + actor.token());
    if (body != null) req.header("Content-Type", "application/json");
    req.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
    return client.send(req.build(), HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode expect(HttpResponse<String> response, int status) throws Exception {
    assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
    return json.readTree(response.body());
  }

  private Actor actor(String role) {
    UUID id = UUID.randomUUID();
    String sub = "user_stage8_" + id;
    jdbc.update("INSERT INTO users(id,clerk_user_id,role) VALUES (?,?,?)", id, sub, role);
    var actor = new Actor(id, TestTokens.token(sub));
    actors.add(actor);
    return actor;
  }

  private UUID createProperty() {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO properties(id,name,slug) VALUES (?,'Stage 8',?)", id, "stage8-" + id);
    return id;
  }

  private UUID createType(UUID p) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO room_types(id,property_id,name,base_price,max_guests,bed_count) VALUES (?,?,'Deluxe',600000,2,1)",
        id,
        p);
    return id;
  }

  private UUID createRoom(UUID p, UUID t, String number) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO rooms(id,property_id,room_type_id,room_number,name) VALUES (?,?,?,?,?)",
        id,
        p,
        t,
        number,
        number + " Deluxe");
    return id;
  }
}
