package com.lumora.booking;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class BookingStatusTest {
  @Test
  void everyTransitionHasAnExplicitPolicy() {
    var allowed =
        Map.of(
            BookingStatus.PENDING_PAYMENT,
                Set.of(BookingStatus.CONFIRMED, BookingStatus.EXPIRED, BookingStatus.CANCELLED),
            BookingStatus.CONFIRMED, Set.of(BookingStatus.CHECKED_IN, BookingStatus.CANCELLED),
            BookingStatus.CHECKED_IN, Set.of(BookingStatus.CHECKED_OUT),
            BookingStatus.CHECKED_OUT, Set.of(BookingStatus.COMPLETED));
    for (var from : BookingStatus.values())
      for (var to : BookingStatus.values()) {
        if (allowed.getOrDefault(from, Set.of()).contains(to)) {
          assertThatCode(() -> from.requireTransitionTo(to)).doesNotThrowAnyException();
        } else {
          assertThatThrownBy(() -> from.requireTransitionTo(to))
              .isInstanceOf(ResponseStatusException.class);
        }
      }
    assertThat(BookingStatus.PENDING_PAYMENT.canTransitionTo(null)).isFalse();
  }
}
