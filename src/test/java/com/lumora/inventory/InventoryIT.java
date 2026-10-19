package com.lumora.inventory;

import static org.assertj.core.api.Assertions.*;

import com.lumora.accesscode.RoomCodeBatch;
import com.lumora.identity.security.ClerkJwtConfiguration;
import com.lumora.support.TestTokens;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "app.clerk.issuer=https://lumora-test.clerk.accounts.dev",
      "app.clerk.authorized-parties=http://localhost:3000",
      "app.room-codes.enabled=false"
    })
@Import(InventoryIT.JwtConfig.class)
class InventoryIT {
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired JsonMapper json;
  @Autowired RoomCodeBatch batch;
  private HttpClient client;
  private String admin, manager, guest;
  private UUID property, otherProperty;
  private final List<String> subjects = new ArrayList<>();
  private final List<UUID> propertyIds = new ArrayList<>();
  private final List<UUID> amenityIds = new ArrayList<>();

  @TestConfiguration(proxyBeanMethods = false)
  static class JwtConfig {
    @Bean
    @Primary
    JwtDecoder testDecoder() throws Exception {
      var decoder = NimbusJwtDecoder.withPublicKey(TestTokens.KEY.toRSAPublicKey()).build();
      decoder.setJwtValidator(ClerkJwtConfiguration.validators(TestTokens.PROPERTIES));
      return decoder;
    }
  }

  @BeforeEach
  void setup() throws Exception {
    client = HttpClient.newHttpClient();
    admin = actor("ADMIN");
    manager = actor("MANAGER");
    guest = actor("USER");
    property = createProperty();
    otherProperty = createProperty();
    jdbc.update(
        "INSERT INTO user_properties(id,user_id,property_id) SELECT ?,id,? FROM users WHERE clerk_user_id=?",
        UUID.randomUUID(),
        property,
        subjects.get(1));
  }

  @AfterEach
  void cleanup() {
    client.close();
    for (UUID p : propertyIds) {
      jdbc.update(
          "DELETE FROM room_access_codes WHERE room_id IN (SELECT id FROM rooms WHERE property_id=?)",
          p);
      jdbc.update("DELETE FROM rooms WHERE property_id=?", p);
      jdbc.update("DELETE FROM room_types WHERE property_id=?", p);
      jdbc.update("DELETE FROM properties WHERE id=?", p);
    }
    for (UUID id : amenityIds) jdbc.update("DELETE FROM amenities WHERE id=?", id);
    for (String subject : subjects) jdbc.update("DELETE FROM users WHERE clerk_user_id=?", subject);
  }

  @Test
  void openApiDocumentsBearerSecurityAndPublicCatalog() throws Exception {
    var paths = expect(send("GET", "/v3/api-docs", null, null), 200).get("paths");
    for (String path :
        List.of(
            "/api/admin/properties",
            "/api/manager/properties/{propertyId}/rooms",
            "/api/admin/room-access-codes/generate")) {
      assertThat(paths.get(path).get("post").get("security").toString()).contains("clerkBearer");
    }
    assertThat(paths.get("/api/properties").get("get").has("security")).isFalse();
  }

  @Test
  void propertyCrudValidationAndPublicVisibility() throws Exception {
    var detail = expect(send("GET", "/api/properties/" + property, null, null), 200);
    assertThat(detail.get("checkInTime").asText()).startsWith("14:00");
    String slug = detail.get("slug").asText();
    assertThat(
            jdbc.queryForObject(
                "SELECT check_in_time FROM properties WHERE id=?", LocalTime.class, property))
        .isEqualTo(LocalTime.of(14, 0));
    assertThat(
            jdbc.queryForObject(
                "SELECT check_out_time FROM properties WHERE id=?", LocalTime.class, property))
        .isEqualTo(LocalTime.NOON);
    assertThat(expect(send("GET", "/api/properties/" + slug, null, null), 200).get("id").asText())
        .isEqualTo(property.toString());
    expect(send("POST", "/api/admin/properties", manager, propertyBody("forbidden")), 403);
    expect(send("PUT", "/api/admin/properties/" + property, guest, propertyBody(slug)), 403);
    expect(send("POST", "/api/admin/properties", admin, propertyBody(slug)), 409);
    var invalid = new HashMap<>(propertyBody("valid-slug"));
    invalid.put("name", " ");
    expect(send("POST", "/api/admin/properties", admin, invalid), 400);
    invalid.put("name", "Valid");
    invalid.put("checkInTime", "25:01");
    expect(send("POST", "/api/admin/properties", admin, invalid), 400);
    invalid.put("checkInTime", "14:00");
    invalid.put("latitude", 91);
    expect(send("POST", "/api/admin/properties", admin, invalid), 400);
    var changed = new HashMap<>(propertyBody(slug));
    changed.put("name", "Updated property");
    assertThat(
            expect(send("PUT", "/api/admin/properties/" + property, admin, changed), 200)
                .get("name")
                .asText())
        .isEqualTo("Updated property");
    expect(send("DELETE", "/api/admin/properties/" + property, admin, null), 204);
    expect(send("GET", "/api/properties/" + property, null, null), 404);
    expect(send("GET", "/api/properties/" + slug, null, null), 404);
    assertThat(expect(send("GET", "/api/properties", null, null), 200).toString())
        .doesNotContain(property.toString());
    assertThat(
            expect(send("GET", "/api/admin/properties/" + property, admin, null), 200)
                .get("status")
                .asText())
        .isEqualTo("INACTIVE");
  }

  @Test
  void scopeValidationAndRoomNumberUniqueness() throws Exception {
    UUID type = createType(property, manager);
    UUID otherType = createType(otherProperty, admin);
    expect(send("POST", base(otherProperty) + "/room-types", manager, typeBody()), 403);
    expect(send("GET", base(otherProperty) + "/rooms", manager, null), 403);
    expect(send("POST", base(property) + "/room-types", guest, typeBody()), 403);
    expect(send("POST", base(property) + "/rooms", manager, roomBody(otherType, "No5")), 404);
    UUID room = createRoom(property, type, "No5", manager);
    expect(send("POST", base(property) + "/rooms", manager, roomBody(type, "No5")), 409);
    createRoom(otherProperty, otherType, "No5", admin);
    expect(send("PUT", base(property) + "/room-types/" + otherType, manager, typeBody()), 404);
    expect(
        send("PUT", base(otherProperty) + "/rooms/" + room, admin, roomBody(otherType, "No5")),
        404);
    var invalid = new HashMap<>(typeBody());
    invalid.put("basePrice", 0);
    expect(send("POST", base(property) + "/room-types", manager, invalid), 400);
    invalid.put("basePrice", 100);
    invalid.put("maxGuests", 0);
    expect(send("POST", base(property) + "/room-types", manager, invalid), 400);
    expect(send("GET", base(property) + "/rooms", null, null), 401);
    expect(send("GET", "/api/properties/not-a-uuid/rooms", null, null), 400);
    // Revoking an assignment immediately revokes mutation rights too.
    jdbc.update("DELETE FROM user_properties WHERE property_id=?", property);
    expect(send("PUT", base(property) + "/rooms/" + room, manager, roomBody(type, "No6")), 403);
  }

  @Test
  void onlyAdminCanBlockAndGenericRoomUpdatesPreserveBlock() throws Exception {
    UUID type = createType(property, manager);
    UUID room = createRoom(property, type, "No5", manager);
    String blockPath = "/api/admin/rooms/" + room;
    expect(send("PATCH", blockPath, manager, Map.of("isBlocked", true)), 403);
    expect(send("PATCH", blockPath, guest, Map.of("isBlocked", true)), 403);
    assertThat(
            expect(send("PATCH", blockPath, admin, Map.of("isBlocked", true)), 200)
                .get("isBlocked")
                .asBoolean())
        .isTrue();
    var edit = new HashMap<>(roomBody(type, "No5"));
    edit.put("isBlocked", false);
    var response = send("PUT", base(property) + "/rooms/" + room, manager, edit);
    assertThat(response.statusCode()).isIn(200, 400);
    assertThat(
            expect(send("GET", base(property) + "/rooms/" + room, manager, null), 200)
                .get("isBlocked")
                .asBoolean())
        .isTrue();
    assertThat(
            expect(send("GET", "/api/properties/" + property + "/rooms", null, null), 200)
                .isEmpty())
        .isTrue();
    expect(send("GET", "/api/properties/" + property + "/rooms/" + room, null, null), 404);
    expect(send("PATCH", blockPath, admin, Map.of("isBlocked", false)), 200);
    expect(send("GET", "/api/properties/" + property + "/rooms/" + room, null, null), 200);
    // A concurrent manager edit must never overwrite a successful admin block.
    var editRequest =
        request("PUT", base(property) + "/rooms/" + room, manager, roomBody(type, "Renamed"));
    var blockRequest = request("PATCH", blockPath, admin, Map.of("isBlocked", true));
    var a = client.sendAsync(editRequest, HttpResponse.BodyHandlers.ofString());
    var b = client.sendAsync(blockRequest, HttpResponse.BodyHandlers.ofString());
    expect(a.join(), 200);
    expect(b.join(), 200);
    assertThat(
            expect(send("GET", base(property) + "/rooms/" + room, manager, null), 200)
                .get("isBlocked")
                .asBoolean())
        .isTrue();
  }

  @Test
  void softDeleteRetainsInventoryAndHidesInactiveParents() throws Exception {
    UUID type = createType(property, manager);
    UUID room = createRoom(property, type, "No5", manager);
    expect(send("DELETE", base(property) + "/room-types/" + type, manager, null), 204);
    expect(send("GET", "/api/properties/" + property + "/room-types/" + type, null, null), 404);
    expect(send("GET", "/api/properties/" + property + "/rooms/" + room, null, null), 404);
    expect(send("POST", base(property) + "/rooms", manager, roomBody(type, "No6")), 409);
    expect(send("GET", base(property) + "/rooms/" + room, manager, null), 200);
    expect(send("PUT", base(property) + "/room-types/" + type, manager, typeBody()), 200);
    expect(send("DELETE", base(property) + "/rooms/" + room, manager, null), 204);
    expect(send("GET", "/api/properties/" + property + "/rooms/" + room, null, null), 404);
    assertThat(
            expect(send("GET", base(property) + "/rooms/" + room, manager, null), 200)
                .get("status")
                .asText())
        .isEqualTo("INACTIVE");
    expect(send("DELETE", "/api/admin/properties/" + property, admin, null), 204);
    expect(send("GET", "/api/properties/" + property + "/room-types", null, null), 404);
    expect(send("GET", "/api/properties/" + property + "/rooms", null, null), 404);
  }

  @Test
  void imagesAreScopedOrderedAndHaveOneCoverAndAmenitiesAreAtomic() throws Exception {
    UUID type = createType(property, manager);
    UUID room = createRoom(property, type, "No5", manager);
    UUID otherType = createType(otherProperty, admin);
    String images = base(property) + "/rooms/" + room + "/images";
    UUID first = id(expect(send("POST", images, manager, imageBody(2, true)), 201));
    UUID second = id(expect(send("POST", images, manager, imageBody(1, true)), 201));
    var detail =
        expect(send("GET", "/api/properties/" + property + "/rooms/" + room, null, null), 200);
    assertThat(detail.get("images").get(0).get("id").asText()).isEqualTo(second.toString());
    assertThat(detail.get("images").get(1).get("isCover").asBoolean()).isFalse();
    expect(send("PUT", images + "/" + first, manager, imageBody(0, true)), 200);
    expect(send("POST", images, guest, imageBody(0, false)), 403);
    expect(
        send(
            "POST",
            base(otherProperty) + "/room-types/" + otherType + "/images",
            manager,
            imageBody(0, false)),
        403);
    expect(
        send(
            "POST",
            base(property) + "/room-types/" + otherType + "/images",
            manager,
            imageBody(0, false)),
        404);
    var invalid = new HashMap<>(imageBody(0, false));
    invalid.put("imageUrl", "javascript:alert(1)");
    expect(send("POST", images, manager, invalid), 400);
    expect(
        send(
            "POST",
            base(property) + "/room-types/" + type + "/images",
            manager,
            imageBody(0, true)),
        201);
    UUID amenity =
        id(
            expect(
                send(
                    "POST",
                    "/api/admin/amenities",
                    admin,
                    Map.of("name", "Wi-Fi " + UUID.randomUUID(), "icon", "wifi")),
                201));
    amenityIds.add(amenity);
    expect(send("POST", "/api/admin/amenities", manager, Map.of("name", "Pool")), 403);
    String selection = base(property) + "/room-types/" + type + "/amenities";
    expect(send("PUT", selection, manager, Map.of("amenityIds", List.of(amenity))), 200);
    expect(send("PUT", selection, manager, Map.of("amenityIds", List.of(UUID.randomUUID()))), 404);
    detail = expect(send("GET", "/api/properties/" + property + "/rooms/" + room, null, null), 200);
    assertThat(detail.get("roomType").get("amenities").get(0).get("id").asText())
        .isEqualTo(amenity.toString());
    assertThat(detail.get("roomType").get("images").size()).isEqualTo(1);
    expect(send("DELETE", "/api/admin/amenities/" + amenity, admin, null), 409);
    expect(
        send(
            "PUT",
            "/api/admin/amenities/" + amenity,
            admin,
            Map.of("name", "Updated " + amenity, "icon", "wifi")),
        200);
    expect(send("DELETE", images + "/" + first, manager, null), 204);
    expect(send("DELETE", images + "/" + first, manager, null), 404);
    expect(send("PUT", selection, manager, Map.of("amenityIds", List.of())), 200);
    expect(send("DELETE", "/api/admin/amenities/" + amenity, admin, null), 204);
  }

  @Test
  void concurrentCoverEditsKeepExactlyOneCover() throws Exception {
    UUID type = createType(property, manager);
    String path = base(property) + "/room-types/" + type + "/images";
    var a =
        client.sendAsync(
            request("POST", path, manager, imageBody(0, true)),
            HttpResponse.BodyHandlers.ofString());
    var b =
        client.sendAsync(
            request("POST", path, manager, imageBody(1, true)),
            HttpResponse.BodyHandlers.ofString());
    expect(a.join(), 201);
    expect(b.join(), 201);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM room_images WHERE room_type_id=? AND is_cover",
                Integer.class,
                type))
        .isEqualTo(1);
  }

  @Test
  void databaseEnforcesCrossPropertyForeignKeysAndImageOwnership() throws Exception {
    UUID type = createType(property, manager);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO rooms(id,property_id,room_type_id,room_number,name,status) VALUES (?,?,?,?,?,'ACTIVE')",
                    UUID.randomUUID(),
                    otherProperty,
                    type,
                    "Invalid",
                    "Invalid"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO room_types(id,property_id,name,base_price,max_guests,bed_count) VALUES (?,?,'Invalid',100,1,1)",
                    UUID.randomUUID(),
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
    UUID room = createRoom(property, type, "No5", manager);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO room_images(id,room_id,room_type_id,image_url) VALUES (?,?,?,'/image.jpg')",
                    UUID.randomUUID(),
                    room,
                    type))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO room_images(id,image_url) VALUES (?,'/image.jpg')",
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update("DELETE FROM room_types WHERE id=?", type))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void codesArePrivateIdempotentConcurrentAndRotateByDate() throws Exception {
    UUID type = createType(property, manager);
    UUID room = createRoom(property, type, "No5", manager);
    UUID inactive = createRoom(property, type, "No6", manager);
    expect(send("DELETE", base(property) + "/rooms/" + inactive, manager, null), 204);
    UUID inactiveType = createType(property, manager);
    UUID underInactiveType = createRoom(property, inactiveType, "No7", manager);
    expect(send("DELETE", base(property) + "/room-types/" + inactiveType, manager, null), 204);
    UUID remoteType = createType(otherProperty, admin);
    UUID underInactiveProperty = createRoom(otherProperty, remoteType, "No8", admin);
    expect(send("DELETE", "/api/admin/properties/" + otherProperty, admin, null), 204);
    // Blocking affects availability, not rotation: an active physical lock still needs a daily
    // code.
    expect(send("PATCH", "/api/admin/rooms/" + room, admin, Map.of("isBlocked", true)), 200);
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    var a = CompletableFuture.supplyAsync(() -> batch.generate(today));
    var b = CompletableFuture.supplyAsync(() -> batch.generate(today));
    assertThat(a.join().generated() + b.join().generated()).isEqualTo(1);
    assertThat(batch.generate(today).generated()).isZero();
    String path = "/api/admin/rooms/" + room + "/access-code?date=" + today;
    var currentResponse = send("GET", path, admin, null);
    var current = expect(currentResponse, 200);
    assertThat(currentResponse.headers().firstValue("Cache-Control")).contains("no-store");
    assertThat(current.get("codeValue").isString()).isTrue();
    assertThat(current.get("codeValue").asText()).matches("[0-9]{6}");
    assertThat(batch.generate(today.plusDays(1)).generated()).isEqualTo(1);
    var next =
        expect(
            send(
                "GET",
                "/api/admin/rooms/" + room + "/access-code?date=" + today.plusDays(1),
                admin,
                null),
            200);
    assertThat(next.get("codeValue").asText()).isNotEqualTo(current.get("codeValue").asText());
    assertThat(expect(send("GET", path, admin, null), 200)).isEqualTo(current);
    for (String token : List.of(manager, guest)) {
      expect(send("GET", path, token, null), 403);
      expect(send("POST", "/api/admin/room-access-codes/generate", token, null), 403);
    }
    expect(send("GET", path, null, null), 401);
    expect(
        send("GET", "/api/properties/" + property + "/rooms/" + room + "/access-code", guest, null),
        403);
    var managed = expect(send("GET", base(property) + "/rooms/" + room, manager, null), 200);
    assertThat(managed.toString()).doesNotContain("codeValue", "generatedAt");
    expect(send("GET", "/api/admin/rooms/" + inactive + "/access-code", admin, null), 404);
    expect(send("GET", "/api/admin/rooms/" + underInactiveType + "/access-code", admin, null), 404);
    expect(
        send("GET", "/api/admin/rooms/" + underInactiveProperty + "/access-code", admin, null),
        404);
    expect(send("GET", "/api/admin/rooms/" + room + "/access-code?date=invalid", admin, null), 400);
    assertThat(
            expect(send("POST", "/api/admin/room-access-codes/generate", admin, null), 200)
                .get("generated")
                .asInt())
        .isZero();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO room_access_codes(id,room_id,code_value,valid_date) VALUES (?,?,'duplicate',?)",
                    UUID.randomUUID(),
                    room,
                    today))
        .isInstanceOf(DataIntegrityViolationException.class);
    jdbc.update(
        "UPDATE room_access_codes SET status='REVOKED' WHERE room_id=? AND valid_date=?",
        room,
        today);
    assertThat(batch.generate(today).generated()).isZero();
    expect(send("GET", path, admin, null), 404);
  }

  private String actor(String role) throws Exception {
    String subject = "user_stage2_" + UUID.randomUUID().toString().replace("-", "");
    subjects.add(subject);
    String token = TestTokens.token(subject);
    expect(send("GET", "/api/me", token, null), 200);
    jdbc.update("UPDATE users SET role=? WHERE clerk_user_id=?", role, subject);
    return token;
  }

  private UUID createProperty() throws Exception {
    UUID id =
        id(
            expect(
                send(
                    "POST",
                    "/api/admin/properties",
                    admin,
                    propertyBody("stage2-" + UUID.randomUUID())),
                201));
    propertyIds.add(id);
    return id;
  }

  private UUID createType(UUID p, String token) throws Exception {
    return id(expect(send("POST", base(p) + "/room-types", token, typeBody()), 201));
  }

  private UUID createRoom(UUID p, UUID type, String number, String token) throws Exception {
    return id(expect(send("POST", base(p) + "/rooms", token, roomBody(type, number)), 201));
  }

  private Map<String, Object> propertyBody(String slug) {
    return Map.of(
        "name",
        "Stage 2 Property",
        "slug",
        slug,
        "checkInTime",
        "14:00",
        "checkOutTime",
        "12:00",
        "status",
        "ACTIVE");
  }

  private Map<String, Object> typeBody() {
    return Map.of(
        "name",
        "Deluxe",
        "basePrice",
        750000,
        "maxGuests",
        2,
        "bedCount",
        1,
        "bedType",
        "QUEEN",
        "sizeM2",
        25,
        "status",
        "ACTIVE");
  }

  private Map<String, Object> roomBody(UUID type, String number) {
    return Map.of(
        "roomTypeId",
        type,
        "roomNumber",
        number,
        "name",
        number + " Deluxe",
        "floor",
        "2",
        "status",
        "ACTIVE");
  }

  private Map<String, Object> imageBody(int order, boolean cover) {
    return Map.of("imageUrl", "/images/room.jpg", "sortOrder", order, "isCover", cover);
  }

  private String base(UUID p) {
    return "/api/manager/properties/" + p;
  }

  private UUID id(JsonNode node) {
    return UUID.fromString(node.get("id").asText());
  }

  private HttpRequest request(String method, String path, String token, Object body) {
    var builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(30))
            .header("X-Request-Id", "stage2-integration")
            .header("Content-Type", "application/json")
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
    if (token != null) builder.header("Authorization", "Bearer " + token);
    return builder.build();
  }

  private HttpResponse<String> send(String method, String path, String token, Object body)
      throws Exception {
    return client.send(request(method, path, token, body), HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode expect(HttpResponse<String> response, int status) {
    assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(status);
    if (status == 204) return null;
    var result = json.readTree(response.body());
    if (status >= 400) {
      assertThat(result.has("code")).isTrue();
      assertThat(result.get("requestId").asText()).isEqualTo("stage2-integration");
    }
    return result;
  }
}
