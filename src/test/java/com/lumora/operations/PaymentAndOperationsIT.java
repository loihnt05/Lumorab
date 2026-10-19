package com.lumora.operations;

import static org.assertj.core.api.Assertions.*;

import com.lumora.accesscode.RoomCodeBatch;
import com.lumora.identity.security.ClerkJwtConfiguration;
import com.lumora.support.TestTokens;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
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
      "app.clerk.authorized-parties=http://localhost:3000",
      "app.room-codes.enabled=false",
      "app.booking.expiry-enabled=false",
      "app.booking.hold-duration=15m",
      "app.payment.mock-enabled=true"
    })
@Import(PaymentAndOperationsIT.TestConfig.class)
class PaymentAndOperationsIT {
  private static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");
  private static final LocalDate IN = LocalDate.of(2026, 10, 19);
  private static final LocalDate OUT = LocalDate.of(2026, 10, 22);

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired JsonMapper json;
  @Autowired TestClock clock;
  @Autowired RoomCodeBatch roomCodeBatch;

  private HttpClient client;
  private UUID property, otherProperty, type, otherType, room, secondRoom, otherRoom;
  private Actor user, otherUser, manager, otherManager, admin;
  private final List<Actor> actors = new ArrayList<>();

  private record Actor(UUID id, String subject, String token) {}

  static class TestClock extends Clock {
    private final AtomicReference<Instant> now = new AtomicReference<>(NOW);

    void set(Instant instant) {
      now.set(instant);
    }

    @Override
    public Instant instant() {
      return now.get();
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return Clock.fixed(instant(), zone);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestConfig {
    @Bean
    @Primary
    TestClock testClock() {
      return new TestClock();
    }

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
    clock.set(NOW);
    client = HttpClient.newHttpClient();
    property = createProperty("Dalat Branch");
    otherProperty = createProperty("Saigon Branch");
    type = createType(property, 600000);
    otherType = createType(otherProperty, 800000);
    room = createRoom(property, type, "No5");
    secondRoom = createRoom(property, type, "No6");
    otherRoom = createRoom(otherProperty, otherType, "S1");
    user = actor("USER", "guest1@lumora.test");
    otherUser = actor("USER", "guest2@lumora.test");
    manager = actor("MANAGER", "mgr.dalat@lumora.test");
    otherManager = actor("MANAGER", "mgr.saigon@lumora.test");
    admin = actor("ADMIN", "admin@lumora.test");
    jdbc.update(
        "INSERT INTO user_properties(id,user_id,property_id) VALUES (?,?,?)",
        UUID.randomUUID(),
        manager.id(),
        property);
    jdbc.update(
        "INSERT INTO user_properties(id,user_id,property_id) VALUES (?,?,?)",
        UUID.randomUUID(),
        otherManager.id(),
        otherProperty);
  }

  @AfterEach
  void cleanup() {
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
    for (var a : actors) {
      jdbc.update("DELETE FROM audit_logs WHERE actor_user_id=?", a.id());
      jdbc.update("DELETE FROM users WHERE id=?", a.id());
    }
  }

  @Test
  void mockPaymentFailRetryIdempotentSuccessAndExpiryProtection() throws Exception {
    UUID bookingId = createBooking(user, room, property, IN, OUT);

    // Non-owner cannot create or view payments for another user's booking
    expect(send("POST", "/api/bookings/" + bookingId + "/payments", otherUser, Map.of()), 404);
    expect(send("GET", "/api/bookings/" + bookingId + "/payments", otherUser, null), 404);

    // First attempt fails -> booking remains PENDING_PAYMENT so guest can retry
    var firstAttempt =
        expect(
            send(
                "POST",
                "/api/bookings/" + bookingId + "/payments",
                user,
                Map.of("amount", 1, "status", "PAID")),
            201);
    UUID firstPaymentId = UUID.fromString(firstAttempt.get("id").asText());
    assertThat(firstAttempt.get("amount").decimalValue()).isEqualByComparingTo("1800000");
    assertThat(firstAttempt.get("provider").asText()).isEqualTo("MOCK");
    assertThat(firstAttempt.get("status").asText()).isEqualTo("PENDING");

    expect(send("POST", "/api/mock-payments/" + firstPaymentId + "/fail", otherUser, null), 404);
    var failed =
        expect(send("POST", "/api/mock-payments/" + firstPaymentId + "/fail", user, null), 200);
    assertThat(failed.get("status").asText()).isEqualTo("FAILED");
    assertThat(failed.get("bookingStatus").asText()).isEqualTo("PENDING_PAYMENT");
    // Repeat fail is idempotent; succeeding a failed payment is rejected
    expect(send("POST", "/api/mock-payments/" + firstPaymentId + "/fail", user, null), 200);
    expect(send("POST", "/api/mock-payments/" + firstPaymentId + "/success", user, null), 409);

    // Retry with second payment attempt -> succeeds and confirms booking atomically
    var secondAttempt =
        expect(send("POST", "/api/bookings/" + bookingId + "/payments", user, Map.of()), 201);
    UUID secondPaymentId = UUID.fromString(secondAttempt.get("id").asText());
    var paid =
        expect(send("POST", "/api/mock-payments/" + secondPaymentId + "/success", user, null), 200);
    assertThat(paid.get("status").asText()).isEqualTo("PAID");
    assertThat(paid.get("bookingStatus").asText()).isEqualTo("CONFIRMED");
    assertThat(paid.get("paidAt").asText()).isEqualTo(NOW.toString());
    assertThat(paid.get("transactionId").asText()).startsWith("MOCK-TX-");

    // Double success is idempotent and does not fail or create duplicate side effects
    var paidAgain =
        expect(send("POST", "/api/mock-payments/" + secondPaymentId + "/success", user, null), 200);
    assertThat(paidAgain.get("id").asText()).isEqualTo(secondPaymentId.toString());
    assertThat(paidAgain.get("status").asText()).isEqualTo("PAID");

    // Confirmed booking cannot create another payment
    expect(send("POST", "/api/bookings/" + bookingId + "/payments", user, Map.of()), 409);

    // Payment list returns both attempts (PAID and FAILED)
    var paymentList =
        expect(send("GET", "/api/bookings/" + bookingId + "/payments", user, null), 200);
    assertThat(paymentList.size()).isEqualTo(2);

    // Expired booking cannot create payment or succeed pending payment
    UUID expiringBookingId = createBooking(user, secondRoom, property, IN, OUT);
    var pendingPay =
        expect(
            send("POST", "/api/bookings/" + expiringBookingId + "/payments", user, Map.of()), 201);
    UUID pendingPayId = UUID.fromString(pendingPay.get("id").asText());
    clock.set(NOW.plus(Duration.ofMinutes(16)));
    expect(send("POST", "/api/bookings/" + expiringBookingId + "/payments", user, Map.of()), 409);
    expect(send("POST", "/api/mock-payments/" + pendingPayId + "/success", user, null), 409);
  }

  @Test
  void internalAmountMismatchNeverConfirmsBooking() throws Exception {
    UUID bookingId = createBooking(user, room, property, IN, OUT);
    var attempt =
        expect(send("POST", "/api/bookings/" + bookingId + "/payments", user, Map.of()), 201);
    UUID paymentId = UUID.fromString(attempt.get("id").asText());
    jdbc.update("UPDATE payments SET amount = 1000 WHERE id = ?", paymentId);
    expect(send("POST", "/api/mock-payments/" + paymentId + "/success", user, null), 409);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM bookings WHERE id = ?", String.class, bookingId))
        .isEqualTo("PENDING_PAYMENT");
  }

  @Test
  void managerDashboardCheckInCheckOutLifecycleAndRoomAccessCodeOperations() throws Exception {
    UUID bookingId = createAndConfirmBooking(user, room, property, IN, OUT);

    // P6.1 Manager Dashboard scope & metrics
    expect(
        send("GET", "/api/manager/properties/" + otherProperty + "/dashboard", manager, null), 403);
    expect(send("GET", "/api/manager/properties/" + property + "/dashboard", user, null), 403);

    var dashboard =
        expect(
            send("GET", "/api/manager/properties/" + property + "/dashboard", manager, null), 200);
    assertThat(dashboard.get("propertyId").asText()).isEqualTo(property.toString());
    assertThat(dashboard.get("totalRooms").asLong()).isEqualTo(2);
    assertThat(dashboard.get("activeRooms").asLong()).isEqualTo(2);
    assertThat(dashboard.get("occupiedRoomsToday").asLong()).isEqualTo(1);
    assertThat(dashboard.get("bookingsToday").asLong()).isEqualTo(1);
    assertThat(dashboard.get("checkInsToday").asLong()).isEqualTo(1);
    assertThat(dashboard.get("upcomingCheckIns").asLong()).isEqualTo(1);
    assertThat(dashboard.get("occupancyRatePercent").decimalValue()).isEqualByComparingTo("50.00");

    // P6.2 Check-in / Check-out lifecycle rules
    // Cannot check-out before check-in
    expect(send("POST", "/api/manager/bookings/" + bookingId + "/check-out", manager, null), 409);
    // Out-of-scope manager gets 403
    expect(
        send("POST", "/api/manager/bookings/" + bookingId + "/check-in", otherManager, null), 403);
    expect(
        send(
            "POST",
            "/api/manager/properties/" + otherProperty + "/bookings/" + bookingId + "/check-in",
            manager,
            null),
        403);
    // Cannot check-in before checkIn date
    clock.set(Instant.parse("2026-10-18T05:00:00Z"));
    expect(send("POST", "/api/manager/bookings/" + bookingId + "/check-in", manager, null), 409);

    // Valid check-in on checkIn date
    clock.set(NOW);
    var checkedIn =
        expect(
            send("POST", "/api/manager/bookings/" + bookingId + "/check-in", manager, null), 200);
    assertThat(checkedIn.get("booking").get("status").asText()).isEqualTo("CHECKED_IN");

    // Valid check-out after check-in
    var checkedOut =
        expect(
            send("POST", "/api/manager/bookings/" + bookingId + "/check-out", manager, null), 200);
    assertThat(checkedOut.get("booking").get("status").asText()).isEqualTo("CHECKED_OUT");

    // Valid complete after check-out
    var completed =
        expect(
            send("POST", "/api/manager/bookings/" + bookingId + "/complete", manager, null), 200);
    assertThat(completed.get("booking").get("status").asText()).isEqualTo("COMPLETED");

    // P6.7 Room Access Code operations
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    roomCodeBatch.generate(today);

    var managerCode =
        expect(
            send(
                "GET",
                "/api/manager/properties/" + property + "/rooms/" + room + "/access-code",
                manager,
                null),
            200);
    assertThat(managerCode.get("codeValue").asText()).matches("[0-9]{6}");
    var directManagerCode =
        expect(send("GET", "/api/manager/rooms/" + room + "/access-code", manager, null), 200);
    assertThat(directManagerCode.get("codeValue").asText())
        .isEqualTo(managerCode.get("codeValue").asText());

    // Out-of-scope manager and regular user cannot read room access code
    expect(
        send(
            "GET",
            "/api/manager/properties/" + otherProperty + "/rooms/" + otherRoom + "/access-code",
            manager,
            null),
        403);
    expect(send("GET", "/api/manager/rooms/" + otherRoom + "/access-code", manager, null), 403);
    expect(send("GET", "/api/manager/rooms/" + room + "/access-code", user, null), 403);
  }

  @Test
  void adminUserManagementAssignmentMonitorMetricsAndAuditLogs() throws Exception {
    UUID dalatBooking = createAndConfirmBooking(user, room, property, IN, OUT);
    createBooking(otherUser, secondRoom, property, IN, OUT); // Remains PENDING_PAYMENT

    // P6.3 Admin user list, role promotion, status disable, and last-admin protection
    var usersPage = expect(send("GET", "/api/admin/users?q=guest1@lumora.test", admin, null), 200);
    assertThat(usersPage.get("totalElements").asLong()).isEqualTo(1);
    assertThat(usersPage.get("items").get(0).get("id").asText()).isEqualTo(user.id().toString());

    // Promote otherUser from USER -> MANAGER
    var promoted =
        expect(
            send(
                "PATCH",
                "/api/admin/users/" + otherUser.id() + "/role",
                admin,
                Map.of("role", "MANAGER")),
            200);
    assertThat(promoted.get("role").asText()).isEqualTo("MANAGER");

    // P6.4 Assign promoted manager to property via
    // /api/admin/managers/{userId}/properties/{propertyId}
    assertThat(
            send(
                    "POST",
                    "/api/admin/managers/" + otherUser.id() + "/properties/" + property,
                    admin,
                    null)
                .statusCode())
        .isEqualTo(204);
    // Duplicate assignment is idempotent
    assertThat(
            send(
                    "POST",
                    "/api/admin/managers/" + otherUser.id() + "/properties/" + property,
                    admin,
                    null)
                .statusCode())
        .isEqualTo(204);
    expect(send("GET", "/api/manager/properties/" + property + "/dashboard", otherUser, null), 200);

    // Revoke assignment immediately revokes scope
    assertThat(
            send(
                    "DELETE",
                    "/api/admin/managers/" + otherUser.id() + "/properties/" + property,
                    admin,
                    null)
                .statusCode())
        .isEqualTo(204);
    expect(send("GET", "/api/manager/properties/" + property + "/dashboard", otherUser, null), 403);

    // Disable otherUser
    var disabled =
        expect(
            send(
                "PATCH",
                "/api/admin/users/" + otherUser.id() + "/status",
                admin,
                Map.of("status", "DISABLED")),
            200);
    assertThat(disabled.get("status").asText()).isEqualTo("DISABLED");
    expect(send("GET", "/api/me", otherUser, null), 403);

    // Last-Admin Guard: if only 1 active ADMIN exists in DB, demoting or disabling it returns 409
    int activeAdmins =
        jdbc.queryForObject(
            "SELECT count(*) FROM users WHERE role='ADMIN' AND status='ACTIVE'", Integer.class);
    if (activeAdmins == 1) {
      expect(
          send("PATCH", "/api/admin/users/" + admin.id() + "/role", admin, Map.of("role", "USER")),
          409);
      expect(
          send(
              "PATCH",
              "/api/admin/users/" + admin.id() + "/status",
              admin,
              Map.of("status", "DISABLED")),
          409);
    }

    // Block secondRoom as Admin so monitor summary reflects blocked room
    expect(send("PATCH", "/api/admin/rooms/" + secondRoom, admin, Map.of("isBlocked", true)), 200);

    // P6.5 Admin Monitor Summary, Revenue & Bookings APIs
    expect(send("GET", "/api/admin/monitor/summary", manager, null), 403);
    expect(send("GET", "/api/admin/monitor/revenue", user, null), 403);

    var summary =
        expect(send("GET", "/api/admin/monitor/summary?propertyId=" + property, admin, null), 200);
    assertThat(summary.get("revenueLabel").asText()).isEqualTo("SIMULATED_MOCK_REVENUE");
    assertThat(summary.get("currency").asText()).isEqualTo("VND");
    assertThat(summary.get("simulatedRevenue").decimalValue()).isEqualByComparingTo("1800000.00");
    assertThat(summary.get("paidPaymentCount").asLong()).isEqualTo(1);
    assertThat(summary.get("totalRooms").asLong()).isEqualTo(2);
    assertThat(summary.get("activeRooms").asLong()).isEqualTo(1);
    assertThat(summary.get("blockedRooms").asLong()).isEqualTo(1);
    assertThat(summary.get("bookingsByStatus").get("CONFIRMED").asLong()).isEqualTo(1);
    assertThat(summary.get("bookingsByStatus").get("PENDING_PAYMENT").asLong()).isEqualTo(1);
    assertThat(summary.get("blockedRoomList").size()).isEqualTo(1);

    var revenue =
        expect(
            send(
                "GET",
                "/api/admin/monitor/revenue?from=2026-10-15&to=2026-10-20&propertyId=" + property,
                admin,
                null),
            200);
    assertThat(revenue.get("totalSimulatedRevenue").decimalValue())
        .isEqualByComparingTo("1800000.00");
    assertThat(revenue.get("paidPaymentCount").asLong()).isEqualTo(1);
    assertThat(revenue.get("dailyTrend").size()).isEqualTo(6);

    var bookingsReport =
        expect(
            send(
                "GET",
                "/api/admin/monitor/bookings?from=2026-10-15&to=2026-10-20&propertyId=" + property,
                admin,
                null),
            200);
    assertThat(bookingsReport.get("totalBookings").asLong()).isEqualTo(2);
    assertThat(bookingsReport.get("dailyTrend").size()).isEqualTo(6);

    // Invalid date range returns 400
    expect(
        send("GET", "/api/admin/monitor/revenue?from=2026-10-25&to=2026-10-20", admin, null), 400);

    // P6.8 Audit logs verification
    expect(send("GET", "/api/admin/audit-logs", manager, null), 403);
    var audits =
        expect(send("GET", "/api/admin/audit-logs?actorUserId=" + admin.id(), admin, null), 200);
    assertThat(audits.get("totalElements").asLong()).isGreaterThanOrEqualTo(5);
    String auditPayload = audits.toString();
    assertThat(auditPayload)
        .contains(
            "USER_ROLE_CHANGE",
            "USER_STATUS_CHANGE",
            "MANAGER_PROPERTY_ASSIGN",
            "MANAGER_PROPERTY_REVOKE",
            "ROOM_BLOCK_CHANGE");
    assertThat(auditPayload).doesNotContain("codeValue");
    assertThat(dalatBooking).isNotNull();
  }

  private UUID createAndConfirmBooking(Actor actor, UUID r, UUID p, LocalDate in, LocalDate out)
      throws Exception {
    UUID bookingId = createBooking(actor, r, p, in, out);
    var payment =
        expect(send("POST", "/api/bookings/" + bookingId + "/payments", actor, Map.of()), 201);
    UUID paymentId = UUID.fromString(payment.get("id").asText());
    expect(send("POST", "/api/mock-payments/" + paymentId + "/success", actor, null), 200);
    return bookingId;
  }

  private UUID createBooking(Actor actor, UUID r, UUID p, LocalDate in, LocalDate out)
      throws Exception {
    Map<String, Object> body =
        Map.of(
            "propertyId",
            p,
            "roomId",
            r,
            "checkIn",
            in,
            "checkOut",
            out,
            "guests",
            2,
            "primaryGuest",
            Map.of("fullName", "Test Guest", "phone", "0901234567", "email", "guest@example.com"));
    var detail = expect(send("POST", "/api/bookings", actor, body), 201);
    return UUID.fromString(detail.get("booking").get("id").asText());
  }

  private UUID createProperty(String name) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO properties(id,name,slug,status) VALUES (?,?,?,'ACTIVE')",
        id,
        name,
        "ops-" + id);
    return id;
  }

  private UUID createType(UUID p, int price) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO room_types(id,property_id,name,base_price,max_guests,bed_count,status) VALUES (?,?,?, ?,2,1,'ACTIVE')",
        id,
        p,
        "Deluxe",
        price);
    return id;
  }

  private UUID createRoom(UUID p, UUID t, String number) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO rooms(id,property_id,room_type_id,room_number,name,status,is_blocked) VALUES (?,?,?,?,?,'ACTIVE',false)",
        id,
        p,
        t,
        number,
        number + " Deluxe");
    return id;
  }

  private Actor actor(String role, String email) {
    String subject = "user_stage6_" + UUID.randomUUID().toString().replace("-", "");
    String token =
        TestTokens.token(subject, c -> c.claim("email", email).claim("first_name", role));
    try {
      var me = expect(send("GET", "/api/me", new Actor(null, subject, token), null), 200);
      UUID id = UUID.fromString(me.get("id").asText());
      jdbc.update("UPDATE users SET role=? WHERE id=?", role, id);
      var created = new Actor(id, subject, token);
      actors.add(created);
      return created;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private HttpResponse<String> send(String method, String path, Actor actor, Object body)
      throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(30))
            .header("X-Request-Id", "stage6-integration")
            .header("Content-Type", "application/json")
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
    if (actor != null) builder.header("Authorization", "Bearer " + actor.token());
    return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode expect(HttpResponse<String> response, int status) {
    assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(status);
    if (status == 204) return null;
    var node = json.readTree(response.body());
    if (status >= 400) {
      assertThat(node.has("code")).isTrue();
      assertThat(node.get("requestId").asText()).isEqualTo("stage6-integration");
    }
    return node;
  }
}
