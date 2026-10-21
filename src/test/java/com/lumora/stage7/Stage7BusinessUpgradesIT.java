package com.lumora.stage7;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumora.accesscode.RoomCodeBatch;
import com.lumora.booking.BookingExpirationService;
import com.lumora.identity.security.ClerkJwtConfiguration;
import com.lumora.payment.PaymentService;
import com.lumora.payment.PaymentViews;
import com.lumora.support.TestTokens;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
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
      "app.booking.hold-duration=15m",
      "app.payment.mock-enabled=true",
      "app.payment.webhook-secret=test-stage7-webhook-secret"
    })
@Import(Stage7BusinessUpgradesIT.TestConfig.class)
class Stage7BusinessUpgradesIT {
  private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");
  private static final LocalDate IN = LocalDate.of(2027, 1, 10);
  private static final LocalDate OUT = LocalDate.of(2027, 1, 13);

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired JsonMapper json;
  @Autowired TestClock clock;
  @Autowired PaymentService paymentService;
  @Autowired BookingExpirationService expirationService;
  @Autowired RoomCodeBatch roomCodeBatch;

  private HttpClient client;
  private UUID property;
  private UUID otherProperty;
  private UUID type;
  private UUID otherType;
  private UUID room;
  private UUID otherRoom;
  private Actor user;
  private Actor otherUser;
  private Actor manager;
  private Actor unassignedManager;
  private Actor admin;
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
    room = createRoom(property, type, "701");
    otherRoom = createRoom(otherProperty, otherType, "702");
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
  void cancellationPolicyTiersRefundsInventoryReleaseAndOutboxEmailsWorkEndToEnd()
      throws Exception {
    // 1. T-7d full refund on confirmed paid booking
    UUID fullBookingId = createConfirmedPaidBooking(IN, OUT);
    var previewFull =
        expect(
            send("GET", "/api/me/bookings/" + fullBookingId + "/cancellation-policy", user, null),
            200);
    assertThat(previewFull.get("canCancel").asBoolean()).isTrue();
    assertThat(previewFull.get("policyTier").asText()).isEqualTo("FULL_REFUND");
    assertThat(previewFull.get("refundPercentage").asInt()).isEqualTo(100);
    assertThat(previewFull.get("refundableAmount").decimalValue())
        .isEqualByComparingTo("1800000.00");

    var cancelFull =
        expect(
            send(
                "POST",
                "/api/me/bookings/" + fullBookingId + "/cancel",
                user,
                Map.of("reason", "Change of travel plans")),
            200);
    assertThat(cancelFull.get("booking").get("booking").get("status").asText())
        .isEqualTo("CANCELLED");
    assertThat(cancelFull.get("refund").get("status").asText()).isEqualTo("REFUNDED");
    assertThat(cancelFull.get("refund").get("refundAmount").decimalValue())
        .isEqualByComparingTo("1800000.00");
    assertThat(cancelFull.get("refund").get("feeAmount").decimalValue())
        .isEqualByComparingTo("0.00");

    // Payment transitioned to REFUNDED and room is immediately available again
    var paymentsAfterCancel =
        expect(send("GET", "/api/bookings/" + fullBookingId + "/payments", user, null), 200);
    assertThat(paymentsAfterCancel.get(0).get("status").asText()).isEqualTo("REFUNDED");
    assertThat(available(IN, OUT)).contains(room.toString());

    // Refund endpoint returns the refund record; repeat cancel is rejected with 409
    var refundNode =
        expect(send("GET", "/api/bookings/" + fullBookingId + "/refund", user, null), 200);
    assertThat(refundNode.get("refundCode").asText()).startsWith("RFD-");
    assertThat(
            send(
                    "POST",
                    "/api/me/bookings/" + fullBookingId + "/cancel",
                    user,
                    Map.of("reason", "Duplicate"))
                .statusCode())
        .isEqualTo(409);

    // Outbox email notifications recorded for BOOKING_CONFIRMED, BOOKING_CANCELLED, REFUND_UPDATED
    var emails =
        expect(
            send("GET", "/api/admin/email-notifications?bookingId=" + fullBookingId, admin, null),
            200);
    assertThat(emails.get("totalElements").asLong()).isEqualTo(3L);
    assertThat(emails.get("items").size()).isEqualTo(3);
    assertThat(emails.toString())
        .contains("BOOKING_CONFIRMED", "BOOKING_CANCELLED", "REFUND_UPDATED")
        .doesNotContain("codeValue", "code_value");

    // 2. T-24h partial refund (50%)
    Instant checkInAnchor = IN.atStartOfDay(VN_ZONE).toInstant();
    clock.set(checkInAnchor.minusSeconds(30 * 3600L)); // 30 hours before check-in
    UUID partialBookingId = createConfirmedPaidBooking(IN, OUT);
    var cancelPartial =
        expect(
            send(
                "POST",
                "/api/bookings/" + partialBookingId + "/cancel",
                user,
                Map.of("reason", "Late change")),
            200);
    assertThat(cancelPartial.get("policy").get("policyTier").asText()).isEqualTo("PARTIAL_REFUND");
    assertThat(cancelPartial.get("refund").get("refundPercentage").asInt()).isEqualTo(50);
    assertThat(cancelPartial.get("refund").get("refundAmount").decimalValue())
        .isEqualByComparingTo("900000.00");
    assertThat(cancelPartial.get("refund").get("feeAmount").decimalValue())
        .isEqualByComparingTo("900000.00");

    // 3. Check-in day no refund (0%)
    clock.set(checkInAnchor.plusSeconds(3600)); // On check-in day
    UUID noRefundBookingId = createConfirmedPaidBooking(IN, OUT);
    var cancelNoRefund =
        expect(
            send(
                "POST",
                "/api/bookings/" + noRefundBookingId + "/cancel",
                user,
                Map.of("reason", "Same-day cancel")),
            200);
    assertThat(cancelNoRefund.get("policy").get("policyTier").asText()).isEqualTo("NO_REFUND");
    assertThat(cancelNoRefund.get("refund").get("status").asText()).isEqualTo("NO_REFUND");
    assertThat(cancelNoRefund.get("refund").get("refundAmount").decimalValue())
        .isEqualByComparingTo("0.00");
    assertThat(cancelNoRefund.get("refund").get("feeAmount").decimalValue())
        .isEqualByComparingTo("1800000.00");
  }

  @Test
  void vietQrWebhookSignatureVerificationIdempotencyAndExpiryGuardWork() throws Exception {
    UUID bookingId = createBooking(user, room, property, IN, OUT);
    var payment =
        expect(
            send(
                "POST",
                "/api/bookings/" + bookingId + "/payments?provider=VIETQR",
                user,
                Map.of("provider", "VIETQR")),
            201);
    UUID paymentId = UUID.fromString(payment.get("id").asText());
    String paymentCode = payment.get("paymentCode").asText();
    assertThat(payment.get("provider").asText()).isEqualTo("VIETQR");

    // Reading Payment QR never confirms the payment or booking
    var qr = expect(send("GET", "/api/payments/" + paymentId + "/qr", user, null), 200);
    assertThat(qr.get("qrType").asText()).isEqualTo("PAYMENT_QR");
    assertThat(qr.get("qrPayload").asText()).startsWith("VIETQR|");
    assertThat(qr.get("status").asText()).isEqualTo("PENDING");
    var bookingStillPending = expect(send("GET", "/api/me/bookings/" + bookingId, user, null), 200);
    assertThat(bookingStillPending.get("booking").get("status").asText())
        .isEqualTo("PENDING_PAYMENT");

    // Forged or missing webhook signature is rejected with 401
    var webhookPayload =
        new PaymentViews.WebhookRequest(
            "evt-stage7-001",
            paymentCode,
            "PAID",
            new BigDecimal("1800000.00"),
            "VND",
            "VQR-TX-001");
    assertThat(sendWebhook("VIETQR", "invalid-signature", webhookPayload).statusCode())
        .isEqualTo(401);
    assertThat(sendWebhook("VIETQR", null, webhookPayload).statusCode()).isEqualTo(401);

    // Valid signed webhook confirms booking & payment
    String validSignature = paymentService.signWebhookPayload(webhookPayload);
    var firstWebhook = expect(sendWebhook("VIETQR", validSignature, webhookPayload), 200);
    assertThat(firstWebhook.get("idempotentReplay").asBoolean()).isFalse();
    assertThat(firstWebhook.get("payment").get("status").asText()).isEqualTo("PAID");
    assertThat(firstWebhook.get("payment").get("bookingStatus").asText()).isEqualTo("CONFIRMED");

    // Replaying the same webhook eventId is idempotent
    var replayWebhook = expect(sendWebhook("VIETQR", validSignature, webhookPayload), 200);
    assertThat(replayWebhook.get("idempotentReplay").asBoolean()).isTrue();
    assertThat(replayWebhook.get("payment").get("status").asText()).isEqualTo("PAID");

    // Expired booking cannot be confirmed by a late webhook
    LocalDate in2 = LocalDate.of(2027, 2, 1);
    LocalDate out2 = LocalDate.of(2027, 2, 3);
    UUID expiredBookingId = createBooking(user, room, property, in2, out2);
    var expiredPayment =
        expect(
            send(
                "POST",
                "/api/bookings/" + expiredBookingId + "/payments?provider=VIETQR",
                user,
                null),
            201);
    String expiredPaymentCode = expiredPayment.get("paymentCode").asText();
    clock.set(NOW.plusSeconds(20 * 60)); // Past 15m hold duration
    expirationService.expire(expiredBookingId, clock.instant());

    var lateWebhook =
        new PaymentViews.WebhookRequest(
            "evt-stage7-late",
            expiredPaymentCode,
            "PAID",
            new BigDecimal("1200000.00"),
            "VND",
            "VQR-TX-LATE");
    String lateSig = paymentService.signWebhookPayload(lateWebhook);
    assertThat(sendWebhook("VIETQR", lateSig, lateWebhook).statusCode()).isEqualTo(409);

    // Admin reconciliation cleans up stale PENDING payment on expired booking
    var reconcileExpired =
        expect(
            send(
                "POST",
                "/api/admin/bookings/" + expiredBookingId + "/reconcile-payment",
                admin,
                Map.of()),
            200);
    assertThat(reconcileExpired.get("actionTaken").asText())
        .isEqualTo("CANCELLED_STALE_PENDING_PAYMENT");
    assertThat(reconcileExpired.get("payment").get("status").asText()).isEqualTo("CANCELLED");
  }

  @Test
  void checkInQrAndPaymentQrRemainStrictlySeparatedAndGuestAccessCodeFollowsStayWindow()
      throws Exception {
    UUID bookingId = createBooking(user, room, property, IN, OUT);
    var payment =
        expect(
            send("POST", "/api/bookings/" + bookingId + "/payments?provider=VIETQR", user, null),
            201);
    UUID paymentId = UUID.fromString(payment.get("id").asText());
    var paymentQr = expect(send("GET", "/api/payments/" + paymentId + "/qr", user, null), 200);
    String paymentQrPayload = paymentQr.get("qrPayload").asText();

    // Before confirmation, check-in QR does not exist yet (409)
    assertThat(
            send("GET", "/api/me/bookings/" + bookingId + "/check-in-qr", user, null).statusCode())
        .isEqualTo(409);

    // Admin reconciles payment as PAID -> confirms booking and generates check-in QR
    var reconciled =
        expect(
            send(
                "POST",
                "/api/admin/payments/" + paymentId + "/reconcile",
                admin,
                Map.of("providerStatus", "PAID", "transactionId", "VQR-RECON-999")),
            200);
    assertThat(reconciled.get("bookingStatus").asText()).isEqualTo("CONFIRMED");

    var checkInQr =
        expect(send("GET", "/api/me/bookings/" + bookingId + "/check-in-qr", user, null), 200);
    String checkInQrToken = checkInQr.get("qrToken").asText();
    assertThat(checkInQr.get("qrType").asText()).isEqualTo("CHECK_IN_QR");
    assertThat(checkInQrToken).startsWith("LMR-CHK-");

    // Passing Payment QR to Check-in QR verification endpoint is rejected with 400 BAD_REQUEST
    assertThat(
            send(
                    "POST",
                    "/api/manager/check-in-qr/verify",
                    manager,
                    Map.of("qrToken", paymentQrPayload))
                .statusCode())
        .isEqualTo(400);

    // Unassigned manager is forbidden (403)
    assertThat(
            send(
                    "POST",
                    "/api/manager/check-in-qr/verify",
                    unassignedManager,
                    Map.of("qrToken", checkInQrToken))
                .statusCode())
        .isEqualTo(403);

    // Assigned manager verifies Check-in QR successfully
    var verified =
        expect(
            send(
                "POST",
                "/api/manager/properties/" + property + "/check-in-qr/verify",
                manager,
                Map.of("qrToken", checkInQrToken)),
            200);
    assertThat(verified.get("valid").asBoolean()).isTrue();
    assertThat(verified.get("bookingId").asText()).isEqualTo(bookingId.toString());
    assertThat(verified.get("checkInWindowOpen").asBoolean()).isFalse();

    // Before check-in day, guest cannot read room access code (409)
    roomCodeBatch.generate(IN);
    assertThat(
            send("GET", "/api/me/bookings/" + bookingId + "/access-code", user, null).statusCode())
        .isEqualTo(409);

    // Advance clock to check-in day in Vietnam
    clock.set(IN.atStartOfDay(VN_ZONE).plusHours(14).toInstant());
    var verifiedOnCheckInDay =
        expect(
            send(
                "POST",
                "/api/manager/check-in-qr/verify",
                manager,
                Map.of("qrToken", checkInQrToken)),
            200);
    assertThat(verifiedOnCheckInDay.get("checkInWindowOpen").asBoolean()).isTrue();

    // Booking owner can now read today's room access code; other user gets 404
    var guestCode =
        expect(send("GET", "/api/me/bookings/" + bookingId + "/access-code", user, null), 200);
    assertThat(guestCode.get("codeValue").asText()).matches("[0-9]{6}");
    assertThat(guestCode.get("validDate").asText()).isEqualTo(IN.toString());
    assertThat(
            send("GET", "/api/me/bookings/" + bookingId + "/access-code", otherUser, null)
                .statusCode())
        .isEqualTo(404);

    // Check-in and then check-out -> after check-out, guest can no longer read room access code
    // (409)
    expect(send("POST", "/api/manager/bookings/" + bookingId + "/check-in", manager, null), 200);
    expect(send("POST", "/api/manager/bookings/" + bookingId + "/check-out", manager, null), 200);
    assertThat(
            send("GET", "/api/me/bookings/" + bookingId + "/access-code", user, null).statusCode())
        .isEqualTo(409);
    assertThat(
            send(
                    "POST",
                    "/api/manager/check-in-qr/verify",
                    manager,
                    Map.of("qrToken", checkInQrToken))
                .statusCode())
        .isEqualTo(409);
  }

  private UUID createConfirmedPaidBooking(LocalDate checkIn, LocalDate checkOut) throws Exception {
    UUID bookingId = createBooking(user, room, property, checkIn, checkOut);
    var payment = expect(send("POST", "/api/bookings/" + bookingId + "/payments", user, null), 201);
    UUID paymentId = UUID.fromString(payment.get("id").asText());
    expect(send("POST", "/api/mock-payments/" + paymentId + "/success", user, null), 200);
    return bookingId;
  }

  private UUID createBooking(Actor a, UUID r, UUID p, LocalDate in, LocalDate out)
      throws Exception {
    var body = new LinkedHashMap<String, Object>();
    body.put("propertyId", p);
    body.put("roomId", r);
    body.put("checkIn", in.toString());
    body.put("checkOut", out.toString());
    body.put("guests", 2);
    body.put(
        "primaryGuest",
        Map.of(
            "fullName", "Stage7 Guest",
            "phone", "0901234567",
            "email", "guest-stage7@example.com"));
    var detail = expect(send("POST", "/api/bookings", a, body), 201);
    return UUID.fromString(detail.get("booking").get("id").asText());
  }

  private String available(LocalDate in, LocalDate out) throws Exception {
    return expect(
            send(
                "GET",
                "/api/public/availability?propertyId="
                    + property
                    + "&checkIn="
                    + in
                    + "&checkOut="
                    + out
                    + "&guests=2",
                null,
                null),
            200)
        .toString();
  }

  private HttpResponse<String> sendWebhook(
      String provider, String signature, PaymentViews.WebhookRequest payload) throws Exception {
    var req =
        HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/webhooks/payments/" + provider))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)));
    if (signature != null) {
      req.header("X-Webhook-Signature", signature);
    }
    return client.send(req.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> send(String method, String path, Actor actor, Object body)
      throws Exception {
    var req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
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
    String sub = "user_stage7_" + id;
    jdbc.update("INSERT INTO users(id,clerk_user_id,role) VALUES (?,?,?)", id, sub, role);
    var actor = new Actor(id, TestTokens.token(sub));
    actors.add(actor);
    return actor;
  }

  private UUID createProperty() {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO properties(id,name,slug) VALUES (?,'Stage 7',?)", id, "stage7-" + id);
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
