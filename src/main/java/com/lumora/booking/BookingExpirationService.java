package com.lumora.booking;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingExpirationService {
  private final BookingRepository bookings;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int expire(UUID id, Instant now) {
    return bookings.expire(id, now);
  }
}
