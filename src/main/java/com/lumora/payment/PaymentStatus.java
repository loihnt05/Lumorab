package com.lumora.payment;

import java.util.EnumSet;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public enum PaymentStatus {
  PENDING,
  PAID,
  FAILED,
  CANCELLED,
  REFUNDED;

  public boolean canTransitionTo(PaymentStatus next) {
    if (next == null) return false;
    return switch (this) {
      case PENDING -> EnumSet.of(PAID, FAILED, CANCELLED).contains(next);
      case PAID -> next == REFUNDED;
      case FAILED, CANCELLED, REFUNDED -> false;
    };
  }

  public void requireTransitionTo(PaymentStatus next) {
    if (!canTransitionTo(next)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid payment status transition.");
    }
  }
}
