package com.lumora.booking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class BookingExpirationWorkerTest {
  @Test
  void oneFailureDoesNotRollbackOrSkipOtherBookings() {
    var repo = mock(BookingRepository.class);
    var service = mock(BookingExpirationService.class);
    var now = Instant.parse("2026-10-19T10:00:00Z");
    var first = UUID.randomUUID();
    var second = UUID.randomUUID();
    var third = UUID.randomUUID();
    when(repo.expiredCandidates(now, 3)).thenReturn(List.of(first, second, third));
    when(service.expire(first, now)).thenThrow(new IllegalStateException("test"));
    when(service.expire(second, now)).thenReturn(1);
    when(service.expire(third, now)).thenReturn(0);
    var worker =
        new BookingExpirationWorker(
            repo,
            service,
            new BookingProperties(Duration.ofMinutes(15), ZoneOffset.UTC, 365, 3),
            Clock.fixed(now, ZoneOffset.UTC));
    assertThat(worker.runBatch()).isEqualTo(new BookingExpirationWorker.Result(3, 1, 1));
    verify(service).expire(third, now);
  }
}
