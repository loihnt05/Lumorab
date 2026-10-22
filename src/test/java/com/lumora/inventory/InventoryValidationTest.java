package com.lumora.inventory;

import static org.assertj.core.api.Assertions.*;

import com.lumora.property.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.LocalTime;
import org.junit.jupiter.api.*;

class InventoryValidationTest {
  private static ValidatorFactory factory;

  @BeforeAll
  static void setup() {
    factory = Validation.buildDefaultValidatorFactory();
  }

  @AfterAll
  static void close() {
    factory.close();
  }

  @Test
  void rejectsInvalidPriceCapacityAndPrecisionBeforeDatabaseWrite() {
    var request =
        new InventoryRequests.RoomTypeRequest(
            "Deluxe",
            null,
            new BigDecimal("0"),
            0,
            null,
            0,
            new BigDecimal("-1"),
            InventoryStatus.ACTIVE);
    assertThat(factory.getValidator().validate(request))
        .extracting(v -> v.getPropertyPath().toString())
        .contains("basePrice", "maxGuests", "bedCount", "sizeM2");
    var excessive =
        new InventoryRequests.RoomTypeRequest(
            "Deluxe", null, new BigDecimal("1.001"), 2, null, 1, null, InventoryStatus.ACTIVE);
    assertThat(factory.getValidator().validate(excessive))
        .extracting(v -> v.getPropertyPath().toString())
        .contains("basePrice");
  }

  @Test
  void rejectsInvalidPropertySlugCoordinatesAndMissingTimes() {
    var request =
        new PropertyRequest(
            " ",
            "Bad Slug",
            null,
            null,
            null,
            new BigDecimal("90.1"),
            new BigDecimal("180.1"),
            null,
            LocalTime.NOON,
            null);
    assertThat(factory.getValidator().validate(request))
        .extracting(v -> v.getPropertyPath().toString())
        .contains("name", "slug", "latitude", "longitude", "checkInTime", "status");
  }

  @Test
  void imageUrlsAllowLocalAndHttpButNotExecutableOrProtocolRelativeUrls() {
    for (String url :
        new String[] {
          "/images/room.jpg", "https://example.test/room.jpg", "http://localhost:8080/room.jpg"
        })
      assertThat(factory.getValidator().validate(new ContentRequests.ImageRequest(url, 0, false)))
          .isEmpty();
    for (String url :
        new String[] {
          "javascript:alert(1)",
          "//example.test/room.jpg",
          "data:text/html,test",
          " ",
          "/bad path.jpg"
        })
      assertThat(factory.getValidator().validate(new ContentRequests.ImageRequest(url, 0, false)))
          .isNotEmpty();
  }
}
