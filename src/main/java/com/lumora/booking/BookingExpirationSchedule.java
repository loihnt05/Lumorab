package com.lumora.booking;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "app.booking.expiry-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class BookingExpirationSchedule {
  private static final Logger log = LoggerFactory.getLogger(BookingExpirationSchedule.class);
  private final BookingExpirationWorker worker;

  @Scheduled(fixedDelayString = "${app.booking.expiry-interval:30s}")
  public void run() {
    try {
      worker.runBatch();
    } catch (RuntimeException exception) {
      log.error("Booking expiry batch failed category={}", exception.getClass().getSimpleName());
    }
  }
}
