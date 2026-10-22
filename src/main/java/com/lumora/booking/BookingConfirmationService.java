package com.lumora.booking;

import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal payment integration boundary. No HTTP route; call only after server payment
 * verification.
 */
@Service
@RequiredArgsConstructor
public class BookingConfirmationService {
  private final BookingRepository bookings;
  private final Clock clock;

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void confirm(UUID id) {
    var rooms = bookings.roomIds(id);
    // Multi-room-ready: deterministic order, room rows before the booking row.
    rooms.forEach(bookings::lockRoom);
    var booking = bookings.lockBooking(id);
    if (booking.status() == BookingStatus.CONFIRMED) return;
    booking.status().requireTransitionTo(BookingStatus.CONFIRMED);
    var now = clock.instant();
    if (rooms.isEmpty() || booking.expiresAt() == null || !booking.expiresAt().isAfter(now))
      throw BookingRepository.conflict();
    for (var roomId : rooms) {
      if (bookings.hasConflict(roomId, booking.checkIn(), booking.checkOut(), now, id))
        throw BookingRepository.conflict();
    }
    bookings.changeStatus(booking, BookingStatus.CONFIRMED);
  }
}
