package com.lumora.booking;

import com.lumora.notification.EmailNotificationService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal payment integration boundary. No HTTP route; call only after server payment
 * verification.
 */
@Service
public class BookingConfirmationService {
  private final BookingRepository bookings;
  private final Clock clock;
  private final EmailNotificationService notifications;

  public BookingConfirmationService(BookingRepository bookings, Clock clock) {
    this(bookings, clock, null);
  }

  @Autowired
  public BookingConfirmationService(
      BookingRepository bookings, Clock clock, EmailNotificationService notifications) {
    this.bookings = bookings;
    this.clock = clock;
    this.notifications = notifications;
  }

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
    if (notifications != null) {
      notifications.enqueueBookingConfirmed(id);
    }
  }
}
