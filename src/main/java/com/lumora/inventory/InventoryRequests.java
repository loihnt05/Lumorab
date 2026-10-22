package com.lumora.inventory;

import com.lumora.property.InventoryStatus;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public final class InventoryRequests {
  private InventoryRequests() {}

  public record RoomTypeRequest(
      @NotBlank @Size(max = 255) String name,
      @Size(max = 10000) String description,
      @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2)
          BigDecimal basePrice,
      @NotNull @Positive Integer maxGuests,
      @Size(max = 100) String bedType,
      @NotNull @Positive Integer bedCount,
      @DecimalMin(value = "0", inclusive = false) @Digits(integer = 6, fraction = 2)
          BigDecimal sizeM2,
      @NotNull InventoryStatus status) {}

  public record RoomRequest(
      @NotNull UUID roomTypeId,
      @NotBlank @Size(max = 50) String roomNumber,
      @NotBlank @Size(max = 255) String name,
      @Size(max = 50) String floor,
      @NotNull InventoryStatus status) {}

  public record BlockRequest(@NotNull Boolean isBlocked) {}
}
