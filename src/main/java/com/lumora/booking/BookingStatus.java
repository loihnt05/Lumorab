package com.lumora.booking;

import java.util.Arrays;
import java.util.List;

/** Inventory semantics only; booking transitions are implemented in Stage 4. */
public enum BookingStatus {
  PENDING_PAYMENT,
  CONFIRMED,
  EXPIRED,
  CANCELLED,
  CHECKED_IN,
  CHECKED_OUT,
  COMPLETED;

  public static List<String> occupiedStatuses() {
    return Arrays.stream(values())
        .filter(status -> status == CONFIRMED || status == CHECKED_IN)
        .map(Enum::name)
        .toList();
  }
}
