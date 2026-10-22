package com.lumora.payment;

import static org.assertj.core.api.Assertions.*;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PaymentStatusTest {
  @Test
  void allowsOnlyConfiguredPaymentTransitions() {
    var allowed =
        Map.of(
            PaymentStatus.PENDING,
            EnumSet.of(PaymentStatus.PAID, PaymentStatus.FAILED, PaymentStatus.CANCELLED),
            PaymentStatus.PAID,
            EnumSet.of(PaymentStatus.REFUNDED),
            PaymentStatus.FAILED,
            Set.<PaymentStatus>of(),
            PaymentStatus.CANCELLED,
            Set.<PaymentStatus>of(),
            PaymentStatus.REFUNDED,
            Set.<PaymentStatus>of());
    for (var from : PaymentStatus.values()) {
      assertThat(from.canTransitionTo(null)).isFalse();
      for (var to : PaymentStatus.values()) {
        if (allowed.get(from).contains(to)) {
          assertThatCode(() -> from.requireTransitionTo(to)).doesNotThrowAnyException();
        } else {
          assertThatThrownBy(() -> from.requireTransitionTo(to))
              .isInstanceOfSatisfying(
                  ResponseStatusException.class,
                  e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        }
      }
    }
  }
}
