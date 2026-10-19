package com.lumora.inventory;

import com.lumora.property.InventoryStatus;
import java.math.BigDecimal;
import java.util.*;

public final class InventoryViews {
  private InventoryViews() {}

  public record Image(UUID id, String imageUrl, int sortOrder, boolean isCover) {}

  public record Amenity(UUID id, String name, String icon) {}

  public record RoomTypeView(
      UUID id,
      UUID propertyId,
      String name,
      String description,
      BigDecimal basePrice,
      int maxGuests,
      String bedType,
      int bedCount,
      BigDecimal sizeM2,
      InventoryStatus status,
      List<Image> images,
      List<Amenity> amenities) {
    public static RoomTypeView from(RoomType t, List<Image> images, List<Amenity> amenities) {
      return new RoomTypeView(
          t.getId(),
          t.getPropertyId(),
          t.getName(),
          t.getDescription(),
          t.getBasePrice(),
          t.getMaxGuests(),
          t.getBedType(),
          t.getBedCount(),
          t.getSizeM2(),
          t.getStatus(),
          images,
          amenities);
    }
  }

  public record RoomView(
      UUID id,
      UUID propertyId,
      UUID roomTypeId,
      String roomNumber,
      String name,
      String floor,
      InventoryStatus status,
      boolean isBlocked,
      RoomTypeView roomType,
      List<Image> images) {
    public static RoomView from(Room r, RoomTypeView type, List<Image> images) {
      return new RoomView(
          r.getId(),
          r.getPropertyId(),
          r.getRoomTypeId(),
          r.getRoomNumber(),
          r.getName(),
          r.getFloor(),
          r.getStatus(),
          r.isBlocked(),
          type,
          images);
    }
  }
}
