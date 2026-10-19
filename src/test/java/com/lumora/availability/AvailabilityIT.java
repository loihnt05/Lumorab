package com.lumora.availability;

import static org.assertj.core.api.Assertions.*;

import com.lumora.booking.BookingStatus;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.net.http.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.clerk.issuer=", "app.room-codes.enabled=false"})
@Import(AvailabilityIT.TimeConfig.class)
class AvailabilityIT {
  private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");
  private static final LocalDate CHECK_IN = LocalDate.of(2027, 1, 10);
  private static final LocalDate CHECK_OUT = LocalDate.of(2027, 1, 13);
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired JsonMapper json;
  @Autowired AvailabilityService availability;
  private HttpClient client;
  private UUID property, type, room, user, amenity;
  private String slug;

  @TestConfiguration(proxyBeanMethods = false)
  static class TimeConfig {
    @Bean
    @Primary
    Clock fixedAvailabilityClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }

  @BeforeEach
  void setup() {
    client = HttpClient.newHttpClient();
    property = UUID.randomUUID();
    type = UUID.randomUUID();
    room = UUID.randomUUID();
    user = UUID.randomUUID();
    amenity = UUID.randomUUID();
    slug = "stage3-" + property;
    jdbc.update(
        "INSERT INTO properties(id,name,slug) VALUES (?,?,?)", property, "Stage 3 property", slug);
    jdbc.update("INSERT INTO users(id,clerk_user_id) VALUES (?,?)", user, "stage3-" + user);
    jdbc.update(
        """
        INSERT INTO room_types(id,property_id,name,base_price,max_guests,bed_count)
        VALUES (?,?,'Deluxe',600000.00,2,1)
        """,
        type,
        property);
    addRoom(room, "No5");
    jdbc.update(
        "INSERT INTO amenities(id,name,icon) VALUES (?,?,'wifi')", amenity, "Wi-Fi-" + amenity);
    jdbc.update(
        "INSERT INTO room_type_amenities(room_type_id,amenity_id) VALUES (?,?)", type, amenity);
    jdbc.update(
        """
        INSERT INTO room_images(id,room_id,image_url,sort_order,is_cover)
        VALUES (?,?,'/images/second.jpg',2,false), (?,?,'/images/first.jpg',1,true)
        """,
        UUID.randomUUID(),
        room,
        UUID.randomUUID(),
        room);
    jdbc.update(
        "INSERT INTO room_images(id,room_type_id,image_url,is_cover) VALUES (?,?,'/images/type.jpg',true)",
        UUID.randomUUID(),
        type);
    jdbc.update(
        "INSERT INTO room_access_codes(id,room_id,code_value,valid_date) VALUES (?,?,'SECRET-123',?)",
        UUID.randomUUID(),
        room,
        CHECK_IN);
  }

  @AfterEach
  void cleanup() {
    client.close();
    jdbc.update("DELETE FROM booking_rooms WHERE property_id=?", property);
    jdbc.update("DELETE FROM bookings WHERE property_id=?", property);
    jdbc.update(
        "DELETE FROM room_access_codes WHERE room_id IN (SELECT id FROM rooms WHERE property_id=?)",
        property);
    jdbc.update("DELETE FROM rooms WHERE property_id=?", property);
    jdbc.update("DELETE FROM room_types WHERE property_id=?", property);
    jdbc.update("DELETE FROM amenities WHERE id=?", amenity);
    jdbc.update("DELETE FROM properties WHERE id=?", property);
    jdbc.update("DELETE FROM users WHERE id=?", user);
  }

  @Test
  void publicCatalogHasOrderedContentWithoutInternalFieldsOrCodes() throws Exception {
    var list = get("/api/public/properties", 200);
    assertThat(list.toString()).contains(slug).doesNotContain("createdAt", "updatedAt", "status");
    var detail = get("/api/public/properties/" + slug, 200);
    assertThat(detail.get("checkInTime").asText()).startsWith("14:00");
    assertThat(detail.get("checkOutTime").asText()).startsWith("12:00");
    get("/api/public/properties/missing-" + UUID.randomUUID(), 404);
    var rooms = get("/api/public/properties/" + property + "/rooms", 200);
    var result = get("/api/public/rooms/" + room, 200);
    assertThat(rooms.get(0)).isEqualTo(result);
    assertThat(result.get("images").get(0).get("imageUrl").asText()).isEqualTo("/images/first.jpg");
    assertThat(result.get("images").get(1).get("imageUrl").asText())
        .isEqualTo("/images/second.jpg");
    assertThat(result.get("roomType").get("images").get(0).get("imageUrl").asText())
        .isEqualTo("/images/type.jpg");
    assertThat(result.get("roomType").get("amenities").get(0).get("id").asText())
        .isEqualTo(amenity.toString());
    assertThat(result.toString())
        .doesNotContain(
            "SECRET-123", "codeValue", "accessCode", "isBlocked", "status", "createdAt");
  }

  @Test
  void availabilityQuotesCurrentDatabasePriceAndDoesNotCacheOrAcceptClientTotals()
      throws Exception {
    var response = send("GET", searchPath() + "&totalAmount=1&pricePerNight=1");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
    var body = json.readTree(response.body());
    assertThat(body.get("checkedAt").asText()).isEqualTo(NOW.toString());
    var quote = body.get("rooms").get(0).get("quote");
    assertThat(quote.get("numberOfNights").asInt()).isEqualTo(3);
    assertThat(quote.get("totalAmount").decimalValue()).isEqualByComparingTo("1800000");
    assertThat(quote.get("currency").asText()).isEqualTo("VND");
    jdbc.update("UPDATE room_types SET base_price=700000 WHERE id=?", type);
    assertThat(
            get(searchPath(), 200)
                .get("rooms")
                .get(0)
                .get("quote")
                .get("totalAmount")
                .decimalValue())
        .isEqualByComparingTo("2100000");
    assertThat(body.toString()).doesNotContain("SECRET-123", "codeValue", "accessCode");
  }

  @ParameterizedTest
  @CsvSource({
    "2027-01-09,2027-01-10,true",
    "2027-01-13,2027-01-14,true",
    "2027-01-09,2027-01-11,false",
    "2027-01-12,2027-01-14,false",
    "2027-01-11,2027-01-12,false",
    "2027-01-09,2027-01-14,false",
    "2027-01-10,2027-01-13,false"
  })
  void overlapUsesHalfOpenDateRanges(LocalDate start, LocalDate end, boolean expectedAvailable)
      throws Exception {
    booking(BookingStatus.CONFIRMED, start, end, null);
    assertAvailable(expectedAvailable);
  }

  @ParameterizedTest
  @EnumSource(BookingStatus.class)
  void allBookingStatusesHaveExplicitInventorySemantics(BookingStatus status) throws Exception {
    booking(status, CHECK_IN, CHECK_OUT, NOW.plusSeconds(60));
    assertAvailable(
        status != BookingStatus.CONFIRMED
            && status != BookingStatus.CHECKED_IN
            && status != BookingStatus.PENDING_PAYMENT);
  }

  @ParameterizedTest
  @CsvSource({"-1,true", "0,true", "1,false"})
  void pendingExpiryIsComparedToOneServerInstant(long seconds, boolean expectedAvailable)
      throws Exception {
    booking(BookingStatus.PENDING_PAYMENT, CHECK_IN, CHECK_OUT, NOW.plusSeconds(seconds));
    assertAvailable(expectedAvailable);
  }

  @Test
  void conflictIsScopedToThePhysicalRoomAndMultipleBookingRoomsAreRespected() throws Exception {
    UUID secondRoom = UUID.randomUUID();
    addRoom(secondRoom, "No6");
    UUID booking = booking(BookingStatus.CONFIRMED, CHECK_IN, CHECK_OUT, null);
    var results = get(searchPath(), 200).get("rooms");
    assertThat(results.size()).isEqualTo(1);
    assertThat(results.get(0).get("room").get("id").asText()).isEqualTo(secondRoom.toString());
    jdbc.update(
        "INSERT INTO booking_rooms(id,booking_id,room_id,property_id) VALUES (?,?,?,?)",
        UUID.randomUUID(),
        booking,
        secondRoom,
        property);
    assertThat(get(searchPath(), 200).get("rooms").isEmpty()).isTrue();
  }

  @Test
  void capacityBlockingAndInactiveInventoryAreExcluded() throws Exception {
    assertThat(get(searchPath().replace("guests=2", "guests=3"), 200).get("rooms").isEmpty())
        .isTrue();
    jdbc.update("UPDATE rooms SET is_blocked=true WHERE id=?", room);
    assertAvailable(false);
    get("/api/public/rooms/" + room, 404);
    jdbc.update("UPDATE rooms SET is_blocked=false,status='INACTIVE' WHERE id=?", room);
    assertAvailable(false);
    get("/api/public/rooms/" + room, 404);
    jdbc.update("UPDATE rooms SET status='ACTIVE' WHERE id=?", room);
    jdbc.update("UPDATE room_types SET status='INACTIVE' WHERE id=?", type);
    assertAvailable(false);
    get("/api/public/rooms/" + room, 404);
    jdbc.update("UPDATE room_types SET status='ACTIVE' WHERE id=?", type);
    jdbc.update("UPDATE properties SET status='INACTIVE' WHERE id=?", property);
    get(searchPath(), 404);
    get("/api/public/rooms/" + room, 404);
    get("/api/public/properties/" + slug, 404);
    get("/api/public/properties/" + property + "/rooms", 404);
    assertThat(get("/api/public/properties", 200).toString()).doesNotContain(slug);
  }

  @Test
  void invalidInputsReturnStructured400AndMissingResourcesReturn404() throws Exception {
    for (String path :
        List.of(
            searchPath().replace("guests=2", "guests=0"),
            searchPath().replace("guests=2", "guests=-1"),
            searchPath().replace("guests=2", "guests=abc"),
            searchPath().replace("&guests=2", ""),
            searchPath().replace("2027-01-13", "2027-01-10"),
            searchPath().replace("2027-01-13", "2027-01-09"),
            searchPath().replace("2027-01-10", "2027-02-30"),
            searchPath().replace("2027-01-10", "garbage"),
            searchPath().replace("&checkIn=2027-01-10", ""),
            searchPath().replace("&checkOut=2027-01-13", ""),
            searchPath().replace(property.toString(), "invalid"),
            "/api/public/availability")) {
      var error = get(path, 400);
      assertThat(
              error.has("code")
                  && error.has("message")
                  && error.has("details")
                  && error.has("requestId"))
          .isTrue();
    }
    get(searchPath().replace(property.toString(), UUID.randomUUID().toString()), 404);
    get("/api/public/rooms/" + UUID.randomUUID(), 404);
    assertThatThrownBy(
            () -> availability.search(new AvailabilityRequest(property, CHECK_OUT, CHECK_IN, 2)))
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  void publicReadRoutesDoNotOpenMutationOrCodeEndpoints() throws Exception {
    assertThat(send("POST", "/api/public/properties").statusCode()).isEqualTo(401);
    assertThat(send("POST", "/api/public/availability").statusCode()).isEqualTo(401);
    assertThat(send("GET", "/api/public/rooms/" + room + "/access-code").statusCode())
        .isEqualTo(401);
    assertThat(send("GET", "/api/admin/rooms/" + room + "/access-code").statusCode())
        .isEqualTo(401);
    var paths = get("/v3/api-docs", 200).get("paths");
    for (String path :
        List.of(
            "/api/public/properties",
            "/api/public/properties/{slug}",
            "/api/public/properties/{propertyId}/rooms",
            "/api/public/rooms/{roomId}",
            "/api/public/availability")) {
      assertThat(paths.get(path).get("get").has("security")).isFalse();
    }
  }

  @Test
  void schemaRejectsInvalidDatesMissingHoldExpiryAndCrossPropertyAssignments() {
    assertThatThrownBy(() -> booking(BookingStatus.CONFIRMED, CHECK_IN, CHECK_IN, null))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> booking(BookingStatus.PENDING_PAYMENT, CHECK_IN, CHECK_OUT, null))
        .isInstanceOf(DataIntegrityViolationException.class);
    UUID booking = booking(BookingStatus.CONFIRMED, CHECK_IN, CHECK_OUT, null);
    UUID otherProperty = UUID.randomUUID();
    UUID otherType = UUID.randomUUID();
    UUID otherRoom = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO properties(id,name,slug) VALUES (?,'Other',?)",
        otherProperty,
        "stage3-" + otherProperty);
    try {
      jdbc.update(
          "INSERT INTO room_types(id,property_id,name,base_price,max_guests,bed_count) VALUES (?,?,'Other',1,1,1)",
          otherType,
          otherProperty);
      jdbc.update(
          "INSERT INTO rooms(id,property_id,room_type_id,room_number,name) VALUES (?,?,?,'Other','Other')",
          otherRoom,
          otherProperty,
          otherType);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "INSERT INTO booking_rooms(id,booking_id,room_id,property_id) VALUES (?,?,?,?)",
                      UUID.randomUUID(),
                      booking,
                      otherRoom,
                      property))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "INSERT INTO booking_rooms(id,booking_id,room_id,property_id) VALUES (?,?,?,?)",
                      UUID.randomUUID(),
                      booking,
                      otherRoom,
                      otherProperty))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      jdbc.update("DELETE FROM rooms WHERE property_id=?", otherProperty);
      jdbc.update("DELETE FROM room_types WHERE property_id=?", otherProperty);
      jdbc.update("DELETE FROM properties WHERE id=?", otherProperty);
    }
  }

  private void addRoom(UUID id, String number) {
    jdbc.update(
        "INSERT INTO rooms(id,property_id,room_type_id,room_number,name) VALUES (?,?,?,?,?)",
        id,
        property,
        type,
        number,
        number + " Deluxe");
  }

  private UUID booking(BookingStatus status, LocalDate start, LocalDate end, Instant expiry) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO bookings(id,user_id,property_id,check_in_date,check_out_date,status,expires_at) VALUES (?,?,?,?,?,?,?)",
        id,
        user,
        property,
        start,
        end,
        status.name(),
        expiry == null ? null : Timestamp.from(expiry));
    jdbc.update(
        "INSERT INTO booking_rooms(id,booking_id,room_id,property_id) VALUES (?,?,?,?)",
        UUID.randomUUID(),
        id,
        room,
        property);
    return id;
  }

  private String searchPath() {
    return "/api/public/availability?propertyId="
        + property
        + "&checkIn="
        + CHECK_IN
        + "&checkOut="
        + CHECK_OUT
        + "&guests=2";
  }

  private void assertAvailable(boolean expected) throws Exception {
    assertThat(get(searchPath(), 200).get("rooms").size()).isEqualTo(expected ? 1 : 0);
  }

  private HttpResponse<String> send(String method, String path) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .method(method, HttpRequest.BodyPublishers.noBody())
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode get(String path, int status) throws Exception {
    var response = send("GET", path);
    assertThat(response.statusCode()).as(path + ": " + response.body()).isEqualTo(status);
    assertThat(response.headers().firstValue("X-Request-Id")).isPresent();
    return json.readTree(response.body());
  }
}
