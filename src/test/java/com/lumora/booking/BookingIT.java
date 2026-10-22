package com.lumora.booking;

import static org.assertj.core.api.Assertions.*;

import com.lumora.identity.security.ClerkJwtConfiguration;
import com.lumora.support.TestTokens;
import java.net.URI;
import java.net.http.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.server.ResponseStatusException;
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
      "app.booking.expiry-batch-size=2"
    })
@Import(BookingIT.TestConfig.class)
class BookingIT {
  private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");
  private static final LocalDate IN = LocalDate.of(2027, 1, 10), OUT = LocalDate.of(2027, 1, 13);
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired DataSource dataSource;
  @Autowired JsonMapper json;
  @Autowired TestClock clock;
  @Autowired BookingConfirmationService confirmation;
  @Autowired BookingExpirationWorker worker;
  @Autowired BookingExpirationService expiration;
  private HttpClient client;
  private UUID property, otherProperty, type, otherType, room, secondRoom, otherRoom;
  private Actor user, otherUser, manager, admin;
  private final List<Actor> actors = new ArrayList<>();

  private record Actor(UUID id, String token) {}

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
    property = createProperty();
    otherProperty = createProperty();
    type = createType(property);
    otherType = createType(otherProperty);
    room = createRoom(property, type, "No5");
    secondRoom = createRoom(property, type, "No6");
    otherRoom = createRoom(otherProperty, otherType, "No5");
    user = actor("USER");
    otherUser = actor("USER");
    manager = actor("MANAGER");
    admin = actor("ADMIN");
    jdbc.update(
        "INSERT INTO user_properties(id,user_id,property_id) VALUES (?,?,?)",
        UUID.randomUUID(),
        manager.id(),
        property);
  }

  @AfterEach
  void cleanup() {
    client.close();
    for (var p : List.of(property, otherProperty)) {
      jdbc.update(
          "DELETE FROM booking_guests WHERE booking_id IN (SELECT id FROM bookings WHERE property_id=?)",
          p);
      jdbc.update("DELETE FROM booking_rooms WHERE property_id=?", p);
      jdbc.update("DELETE FROM bookings WHERE property_id=?", p);
      jdbc.update("DELETE FROM rooms WHERE property_id=?", p);
      jdbc.update("DELETE FROM room_types WHERE property_id=?", p);
      jdbc.update("DELETE FROM properties WHERE id=?", p);
    }
    for (var actor : actors) jdbc.update("DELETE FROM users WHERE id=?", actor.id());
  }

  @Test
  void createsHoldWithServerPriceAndImmutableSnapshots() throws Exception {
    var body = body(room, property, IN, OUT);
    body.put("userId", otherUser.id());
    body.put("status", "CONFIRMED");
    body.put("totalAmount", 1);
    body.put("pricePerNight", 1);
    body.put("expiresAt", "2099-01-01T00:00:00Z");
    var response = send("POST", "/api/bookings", user, body);
    var detail = expect(response, 201);
    var id = id(detail);
    assertThat(response.headers().firstValue("Location")).hasValue("/api/me/bookings/" + id);
    assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
    assertThat(detail.get("booking").get("status").asText()).isEqualTo("PENDING_PAYMENT");
    assertThat(detail.get("booking").get("expiresAt").asText())
        .isEqualTo(NOW.plusSeconds(900).toString());
    assertThat(detail.get("booking").get("totalAmount").decimalValue())
        .isEqualByComparingTo("1800000");
    assertThat(detail.get("rooms").get(0).get("numberOfNights").asInt()).isEqualTo(3);
    assertThat(detail.get("guests").get(0).get("fullName").asText()).isEqualTo("Test Guest");
    assertThat(jdbc.queryForObject("SELECT user_id FROM bookings WHERE id=?", UUID.class, id))
        .isEqualTo(user.id());
    assertThat(available()).doesNotContain(room.toString());
    jdbc.update("UPDATE room_types SET base_price=700000 WHERE id=?", type);
    jdbc.update("UPDATE rooms SET name='Renamed' WHERE id=?", room);
    var saved = expect(send("GET", "/api/me/bookings/" + id, user, null), 200);
    assertThat(saved.get("booking").get("totalAmount").decimalValue())
        .isEqualByComparingTo("1800000");
    assertThat(saved.get("rooms").get(0).get("roomName").asText()).isEqualTo("No5 Deluxe");
    assertThat(saved.toString()).doesNotContain("accessCode", "codeValue");
  }

  @Test
  void requotesAfterSearchAndRejectsChangedInventory() throws Exception {
    assertThat(available()).contains(room.toString());
    jdbc.update("UPDATE room_types SET base_price=750000 WHERE id=?", type);
    var detail = create(user, room, property, IN, OUT);
    assertThat(detail.get("booking").get("totalAmount").decimalValue())
        .isEqualByComparingTo("2250000");
    jdbc.update("UPDATE rooms SET is_blocked=true WHERE id=?", secondRoom);
    expect(send("POST", "/api/bookings", user, body(secondRoom, property, IN, OUT)), 409);
    jdbc.update("UPDATE rooms SET is_blocked=false,status='INACTIVE' WHERE id=?", secondRoom);
    expect(send("POST", "/api/bookings", user, body(secondRoom, property, IN, OUT)), 409);
    jdbc.update("UPDATE rooms SET status='ACTIVE' WHERE id=?", secondRoom);
    jdbc.update("UPDATE room_types SET status='INACTIVE' WHERE id=?", type);
    expect(send("POST", "/api/bookings", user, body(secondRoom, property, IN, OUT)), 409);
    jdbc.update("UPDATE room_types SET status='ACTIVE' WHERE id=?", type);
    jdbc.update("UPDATE properties SET status='INACTIVE' WHERE id=?", property);
    expect(send("POST", "/api/bookings", user, body(secondRoom, property, IN, OUT)), 409);
    assertThat(countBookings(property)).isEqualTo(1);
  }

  @Test
  void validatesDatesCapacityPrimaryGuestAndRoomProperty() throws Exception {
    for (var bad :
        List.of(
            "same", "reverse", "past", "tooLong", "zeroGuests", "missingGuest", "invalidEmail")) {
      var body = body(room, property, IN, OUT);
      switch (bad) {
        case "same" -> body.put("checkOut", IN);
        case "reverse" -> body.put("checkOut", IN.minusDays(1));
        case "past" -> {
          body.put("checkIn", LocalDate.of(2026, 10, 18));
          body.put("checkOut", LocalDate.of(2026, 10, 20));
        }
        case "tooLong" -> body.put("checkOut", IN.plusDays(366));
        case "zeroGuests" -> body.put("guests", 0);
        case "missingGuest" -> body.remove("primaryGuest");
        case "invalidEmail" ->
            body.put(
                "primaryGuest", Map.of("fullName", "Guest", "phone", "123456", "email", "bad"));
      }
      expect(send("POST", "/api/bookings", user, body), 400);
    }
    var tooMany = body(room, property, IN, OUT);
    tooMany.put("guests", 3);
    expect(send("POST", "/api/bookings", user, tooMany), 409);
    expect(send("POST", "/api/bookings", user, body(otherRoom, property, IN, OUT)), 404);
    expect(send("POST", "/api/bookings", user, body(UUID.randomUUID(), property, IN, OUT)), 404);
    assertThat(countBookings(property)).isZero();
  }

  @Test
  void bookingDateUsesVietnamCalendarDay() throws Exception {
    clock.set(Instant.parse("2026-10-19T17:01:00Z")); // Already October 20 in Vietnam.
    expect(
        send(
            "POST",
            "/api/bookings",
            user,
            body(room, property, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 21))),
        400);
    create(user, room, property, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21));
  }

  @Test
  void authorizationOwnerIsolationAndProtectedConfirmation() throws Exception {
    expect(send("POST", "/api/bookings", null, body(room, property, IN, OUT)), 401);
    expect(send("POST", "/api/bookings", manager, body(room, property, IN, OUT)), 403);
    var booking = create(user, room, property, IN, OUT);
    var id = id(booking);
    expect(send("GET", "/api/me/bookings/" + id, otherUser, null), 404);
    assertThat(
            expect(send("GET", "/api/me/bookings", otherUser, null), 200)
                .get("totalElements")
                .asLong())
        .isZero();
    expect(send("GET", "/api/me/bookings", null, null), 401);
    expect(send("POST", "/api/bookings/" + id + "/confirm", user, null), 403);
    expect(send("GET", "/api/admin/bookings", user, null), 403);
    expect(send("GET", "/api/manager/properties/" + property + "/bookings", user, null), 403);
    jdbc.update("UPDATE users SET status='DISABLED' WHERE id=?", user.id());
    assertThat(
            send("POST", "/api/bookings", user, body(secondRoom, property, IN, OUT)).statusCode())
        .isIn(401, 403);
    assertThat(countBookings(property)).isEqualTo(1);
  }

  @Test
  void managerFiltersPaginationAndAdminGlobalScope() throws Exception {
    var first = create(user, room, property, IN, OUT);
    var firstId = id(first);
    create(otherUser, secondRoom, property, IN, OUT);
    var outside = create(otherUser, otherRoom, otherProperty, IN, OUT);
    String base = "/api/manager/properties/" + property + "/bookings";
    var page = expect(send("GET", base + "?size=1", manager, null), 200);
    assertThat(page.get("totalElements").asLong()).isEqualTo(2);
    assertThat(page.get("items").size()).isEqualTo(1);
    var next = expect(send("GET", base + "?size=1&page=1", manager, null), 200);
    assertThat(next.get("items").get(0).get("id")).isNotEqualTo(page.get("items").get(0).get("id"));
    assertThat(
            expect(send("GET", base + "?date=" + OUT, manager, null), 200)
                .get("totalElements")
                .asLong())
        .isZero();
    String code = first.get("booking").get("bookingCode").asText();
    assertThat(
            expect(
                    send(
                        "GET",
                        base
                            + "?date="
                            + IN
                            + "&status=PENDING_PAYMENT&roomId="
                            + room
                            + "&bookingCode="
                            + code,
                        manager,
                        null),
                    200)
                .get("totalElements")
                .asLong())
        .isEqualTo(1);
    expect(send("GET", base + "/" + firstId, manager, null), 200);
    expect(send("GET", base + "/" + id(outside), manager, null), 404);
    expect(
        send("GET", "/api/manager/properties/" + otherProperty + "/bookings", manager, null), 403);
    assertThat(
            expect(send("GET", "/api/admin/bookings?propertyId=" + otherProperty, admin, null), 200)
                .get("totalElements")
                .asLong())
        .isEqualTo(1);
    expect(send("GET", "/api/admin/bookings/" + id(outside), admin, null), 200);
    expect(send("GET", "/api/manager/properties/" + otherProperty + "/bookings", admin, null), 200);
    for (var suffix : List.of("?size=0", "?size=101", "?page=-1", "?status=FAKE", "?date=invalid"))
      expect(send("GET", base + suffix, manager, null), 400);
    jdbc.update("DELETE FROM user_properties WHERE user_id=?", manager.id());
    expect(send("GET", base, manager, null), 403);
  }

  @Test
  void concurrentOverlappingRequestsHaveExactlyOneWinner() throws Exception {
    var responses =
        raceWhileRoomLocked(
            () -> send("POST", "/api/bookings", user, body(room, property, IN, OUT)),
            () ->
                send(
                    "POST",
                    "/api/bookings",
                    otherUser,
                    body(room, property, IN.plusDays(1), OUT.plusDays(1))));
    assertThat(responses.stream().map(HttpResponse::statusCode).toList())
        .containsExactlyInAnyOrder(201, 409);
    assertThat(countBookings(property)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM booking_rooms WHERE property_id=?", Long.class, property))
        .isEqualTo(1);
  }

  @Test
  void adjacentDatesAndDifferentRoomsCanBookConcurrently() throws Exception {
    var adjacent =
        raceWhileRoomLocked(
            () -> send("POST", "/api/bookings", user, body(room, property, IN, OUT)),
            () ->
                send(
                    "POST",
                    "/api/bookings",
                    otherUser,
                    body(room, property, OUT, OUT.plusDays(2))));
    assertThat(adjacent.stream().map(HttpResponse::statusCode).toList()).containsOnly(201);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var a =
          executor.submit(
              () -> send("POST", "/api/bookings", user, body(secondRoom, property, IN, OUT)));
      var b =
          executor.submit(
              () ->
                  send(
                      "POST", "/api/bookings", otherUser, body(otherRoom, otherProperty, IN, OUT)));
      expect(a.get(15, TimeUnit.SECONDS), 201);
      expect(b.get(15, TimeUnit.SECONDS), 201);
    }
  }

  @Test
  void createWaitsForConcurrentBlockAndRechecksCommittedValue() throws Exception {
    try (var connection = dataSource.getConnection();
        var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      connection.setAutoCommit(false);
      try (var statement =
          connection.prepareStatement("UPDATE rooms SET is_blocked=true WHERE id=?")) {
        statement.setObject(1, room);
        statement.executeUpdate();
      }
      var pending =
          executor.submit(() -> send("POST", "/api/bookings", user, body(room, property, IN, OUT)));
      try {
        awaitRoomWaiters(1);
      } finally {
        connection.commit();
      }
      expect(pending.get(15, TimeUnit.SECONDS), 409);
    }
    assertThat(countBookings(property)).isZero();
  }

  @Test
  void expiredHoldReleasesRoomBeforeWorkerAndCannotBeConfirmedLate() throws Exception {
    UUID old = id(create(user, room, property, IN, OUT));
    clock.set(NOW.plusSeconds(900));
    assertThat(available()).contains(room.toString());
    UUID fresh = id(create(otherUser, room, property, IN, OUT));
    assertThatThrownBy(() -> confirmation.confirm(old)).isInstanceOf(ResponseStatusException.class);
    assertThat(status(old)).isEqualTo("PENDING_PAYMENT");
    assertThat(worker.runBatch().expired()).isEqualTo(1);
    assertThat(status(old)).isEqualTo("EXPIRED");
    assertThat(status(fresh)).isEqualTo("PENDING_PAYMENT");
    assertThat(worker.runBatch().expired()).isZero();
  }

  @Test
  void confirmationIsIdempotentAndExpiryNeverOverwritesIt() throws Exception {
    UUID id = id(create(user, room, property, IN, OUT));
    confirmation.confirm(id);
    confirmation.confirm(id);
    clock.set(NOW.plusSeconds(901));
    assertThat(expiration.expire(id, clock.instant())).isZero();
    assertThat(status(id)).isEqualTo("CONFIRMED");
    expect(send("POST", "/api/bookings", otherUser, body(room, property, IN, OUT)), 409);
  }

  @Test
  void lateConfirmationRacingNewHoldCannotDoubleBook() throws Exception {
    UUID old = id(create(user, room, property, IN, OUT));
    clock.set(NOW.plusSeconds(900));
    var results =
        raceWhileRoomLocked(
            () -> {
              try {
                confirmation.confirm(old);
                return 200;
              } catch (ResponseStatusException e) {
                return e.getStatusCode().value();
              }
            },
            () ->
                send("POST", "/api/bookings", otherUser, body(room, property, IN, OUT))
                    .statusCode());
    assertThat(results).containsExactlyInAnyOrder(409, 201);
    assertThat(status(old)).isEqualTo("PENDING_PAYMENT");
  }

  @Test
  void confirmationAndExpirationSerializeOnBookingState() throws Exception {
    UUID id = id(create(user, room, property, IN, OUT));
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var confirmed =
          executor.submit(
              () -> {
                try {
                  confirmation.confirm(id);
                  return true;
                } catch (ResponseStatusException e) {
                  return false;
                }
              });
      var expired = executor.submit(() -> expiration.expire(id, NOW.plusSeconds(900)));
      boolean success = confirmed.get(15, TimeUnit.SECONDS);
      int changed = expired.get(15, TimeUnit.SECONDS);
      assertThat(status(id)).isEqualTo(success ? "CONFIRMED" : "EXPIRED");
      assertThat(changed).isEqualTo(success ? 0 : 1);
    }
  }

  @Test
  void expiryUsesBoundedBatchesAndSupportsConcurrentWorkers() throws Exception {
    for (int day = 0; day < 3; day++)
      create(user, room, property, IN.plusDays(day), IN.plusDays(day + 1));
    clock.set(NOW.plusSeconds(900));
    var first = worker.runBatch();
    assertThat(first.selected()).isEqualTo(2);
    assertThat(first.expired()).isEqualTo(2);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var a = executor.submit(worker::runBatch);
      var b = executor.submit(worker::runBatch);
      assertThat(a.get(15, TimeUnit.SECONDS).expired() + b.get(15, TimeUnit.SECONDS).expired())
          .isEqualTo(1);
    }
    assertThat(worker.runBatch().selected()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM bookings WHERE property_id=? AND status='EXPIRED'",
                Long.class,
                property))
        .isEqualTo(3);
  }

  @Test
  void allBookingWritesRollbackIfGuestPersistenceFails() throws Exception {
    String constraint = "stage4_guest_failure_" + UUID.randomUUID().toString().replace("-", "");
    jdbc.execute(
        "ALTER TABLE booking_guests ADD CONSTRAINT "
            + constraint
            + " CHECK (full_name <> 'Stage4Failure')");
    try {
      var request = body(room, property, IN, OUT);
      request.put(
          "primaryGuest",
          Map.of(
              "fullName", "Stage4Failure", "phone", "0900000000", "email", "guest@example.test"));
      expect(send("POST", "/api/bookings", user, request), 409);
      assertThat(countBookings(property)).isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM booking_rooms WHERE property_id=?", Long.class, property))
          .isZero();
      create(user, room, property, IN, OUT);
    } finally {
      jdbc.execute("ALTER TABLE booking_guests DROP CONSTRAINT " + constraint);
    }
  }

  @Test
  void schemaAndOpenApiExposeExpectedConstraintsAndRoutes() throws Exception {
    UUID id = id(create(user, room, property, IN, OUT));
    UUID other = id(create(otherUser, secondRoom, property, IN, OUT));
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE bookings SET booking_code=(SELECT booking_code FROM bookings WHERE id=?) WHERE id=?",
                    id,
                    other))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> jdbc.update("UPDATE booking_rooms SET room_total=1 WHERE booking_id=?", id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    var paths = expect(send("GET", "/v3/api-docs", null, null), 200).get("paths");
    assertThat(paths.get("/api/bookings").get("post").get("security").toString())
        .contains("clerkBearer");
    assertThat(paths.get("/api/me/bookings").get("get").get("security").toString())
        .contains("clerkBearer");
  }

  private <T> List<T> raceWhileRoomLocked(Callable<T> first, Callable<T> second) throws Exception {
    try (var connection = dataSource.getConnection();
        var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      connection.setAutoCommit(false);
      try (var statement =
          connection.prepareStatement("SELECT id FROM rooms WHERE id=? FOR UPDATE")) {
        statement.setObject(1, room);
        statement.executeQuery().close();
      }
      var a = executor.submit(first);
      var b = executor.submit(second);
      try {
        awaitRoomWaiters(2);
      } finally {
        connection.commit();
      }
      return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
    }
  }

  private void awaitRoomWaiters(int expected) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    long count = 0;
    while (System.nanoTime() < deadline) {
      count =
          jdbc.queryForObject(
              """
          SELECT count(*) FROM pg_stat_activity WHERE datname=current_database()
          AND wait_event_type='Lock' AND query ILIKE '%FROM rooms%FOR UPDATE%'
          """,
              Long.class);
      if (count >= expected) return;
      Thread.sleep(25);
    }
    assertThat(count)
        .as("requests must contend on actual PostgreSQL room locks")
        .isGreaterThanOrEqualTo(expected);
  }

  private Actor actor(String role) {
    UUID id = UUID.randomUUID();
    String subject = "user_stage4_" + id;
    jdbc.update("INSERT INTO users(id,clerk_user_id,role) VALUES (?,?,?)", id, subject, role);
    var actor = new Actor(id, TestTokens.token(subject));
    actors.add(actor);
    return actor;
  }

  private UUID createProperty() {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO properties(id,name,slug) VALUES (?,'Stage 4',?)", id, "stage4-" + id);
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

  private Map<String, Object> body(UUID r, UUID p, LocalDate in, LocalDate out) {
    var body = new HashMap<String, Object>();
    body.put("propertyId", p);
    body.put("roomId", r);
    body.put("checkIn", in);
    body.put("checkOut", out);
    body.put("guests", 2);
    body.put(
        "primaryGuest",
        Map.of("fullName", "Test Guest", "phone", "0900000000", "email", "guest@example.test"));
    return body;
  }

  private JsonNode create(Actor actor, UUID r, UUID p, LocalDate in, LocalDate out)
      throws Exception {
    return expect(send("POST", "/api/bookings", actor, body(r, p, in, out)), 201);
  }

  private UUID id(JsonNode detail) {
    return UUID.fromString(detail.get("booking").get("id").asText());
  }

  private String status(UUID id) {
    return jdbc.queryForObject("SELECT status FROM bookings WHERE id=?", String.class, id);
  }

  private long countBookings(UUID p) {
    return jdbc.queryForObject("SELECT count(*) FROM bookings WHERE property_id=?", Long.class, p);
  }

  private String available() throws Exception {
    return expect(
            send(
                "GET",
                "/api/public/availability?propertyId="
                    + property
                    + "&checkIn="
                    + IN
                    + "&checkOut="
                    + OUT
                    + "&guests=2",
                null,
                null),
            200)
        .toString();
  }

  private HttpResponse<String> send(String method, String path, Actor actor, Object body)
      throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .header("X-Request-Id", "stage4-test")
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
    if (actor != null) request.header("Authorization", "Bearer " + actor.token());
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode expect(HttpResponse<String> response, int status) {
    assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(status);
    assertThat(response.headers().firstValue("X-Request-Id")).hasValue("stage4-test");
    var result = json.readTree(response.body());
    if (status >= 400) assertThat(result.has("code") && result.has("requestId")).isTrue();
    return result;
  }
}
