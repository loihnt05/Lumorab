package com.lumora.booking;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookingExpirationWorker {
  private static final Logger log = LoggerFactory.getLogger(BookingExpirationWorker.class);
  private final BookingRepository bookings;
  private final BookingExpirationService expiration;
  private final BookingProperties properties;
  private final Clock clock;

  public record Result(int selected, int expired, int failed) {}

  public Result runBatch() {
    var now = clock.instant();
    var ids = bookings.expiredCandidates(now, properties.expiryBatchSize());
    int expired = 0, failed = 0;
    for (var id : ids) {
      try {
        expired += expiration.expire(id, now);
      } catch (RuntimeException exception) {
        failed++;
        log.error(
            "Booking expiry failed bookingId={} category={}",
            id,
            exception.getClass().getSimpleName());
      }
    }
    if (!ids.isEmpty())
      log.info("Booking expiry selected={} expired={} failed={}", ids.size(), expired, failed);
    return new Result(ids.size(), expired, failed);
  }
}
