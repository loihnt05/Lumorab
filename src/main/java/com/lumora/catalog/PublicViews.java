package com.lumora.catalog;

import com.lumora.inventory.InventoryViews.Amenity;
import com.lumora.inventory.InventoryViews.Image;
import com.lumora.property.PropertyDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class PublicViews {
  private PublicViews() {}

  @Schema(name = "PublicPropertySummary")
  public record PropertySummary(UUID id, String name, String slug, String address, String city) {
    static PropertySummary from(PropertyDetail p) {
      return new PropertySummary(p.id(), p.name(), p.slug(), p.address(), p.city());
    }
  }

  @Schema(name = "PublicPropertyDetail")
  public record Property(
      UUID id,
      String name,
      String slug,
      String description,
      String address,
      String city,
      BigDecimal latitude,
      BigDecimal longitude,
      LocalTime checkInTime,
      LocalTime checkOutTime) {
    static Property from(PropertyDetail p) {
      return new Property(
          p.id(),
          p.name(),
          p.slug(),
          p.description(),
          p.address(),
          p.city(),
          p.latitude(),
          p.longitude(),
          p.checkInTime(),
          p.checkOutTime());
    }
  }

  @Schema(name = "PublicRoomType")
  public record RoomType(
      UUID id,
      String name,
      String description,
      BigDecimal basePrice,
      int maxGuests,
      String bedType,
      int bedCount,
      BigDecimal sizeM2,
      List<Image> images,
      List<Amenity> amenities) {}

  @Schema(name = "PublicRoom")
  public record Room(
      UUID id,
      UUID propertyId,
      String roomNumber,
      String name,
      String floor,
      RoomType roomType,
      List<Image> images) {
    Room withContent(List<Image> roomImages, List<Image> typeImages, List<Amenity> amenities) {
      var t = roomType;
      return new Room(
          id,
          propertyId,
          roomNumber,
          name,
          floor,
          new RoomType(
              t.id(),
              t.name(),
              t.description(),
              t.basePrice(),
              t.maxGuests(),
              t.bedType(),
              t.bedCount(),
              t.sizeM2(),
              typeImages,
              amenities),
          roomImages);
    }
  }
}
