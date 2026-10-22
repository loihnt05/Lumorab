package com.lumora.booking;

import java.util.Arrays;
import java.util.List;

public enum BookingStatus {
  PENDING_PAYMENT,
  CONFIRMED,
  EXPIRED,
  CANCELLED,
  CHECKED_IN,
  CHECKED_OUT,
  COMPLETED;

  public boolean canTransitionTo(BookingStatus next) {
    if (next == null) return false;
    return switch (this) {
      case PENDING_PAYMENT -> next == CONFIRMED || next == EXPIRED || next == CANCELLED;
      case CONFIRMED -> next == CHECKED_IN || next == CANCELLED;
      case CHECKED_IN -> next == CHECKED_OUT;
      case CHECKED_OUT -> next == COMPLETED;
      case EXPIRED, CANCELLED, COMPLETED -> false;
    };
  }

  public void requireTransitionTo(BookingStatus next) {
    if (!canTransitionTo(next))
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT, "Invalid booking state transition.");
  }

  public static List<String> occupiedStatuses() {
    return Arrays.stream(values())
        .filter(status -> status == CONFIRMED || status == CHECKED_IN)
        .map(Enum::name)
        .toList();
  }
}
