package com.lumora.booking;

import com.lumora.pricing.PricingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingService {
  private final BookingRepository bookings;
  private final PricingService pricing;
  private final BookingProperties properties;
  private final Clock clock;

  @Transactional(isolation = Isolation.READ_COMMITTED)
  @PreAuthorize("hasRole('USER') && #userId == authentication.principal.id")
  public BookingViews.Detail create(UUID userId, @NotNull @Valid CreateBookingRequest request) {
    // Lock the physical room first. Every create/confirmation uses this order.
    var room = bookings.lockRoom(request.roomId());
    if (!room.propertyId().equals(request.propertyId()))
      throw com.lumora.property.PropertyService.notFound();
    var rate = bookings.lockRate(room);
    var now = clock.instant();
    if (request.checkIn().isBefore(LocalDate.ofInstant(now, properties.zone()))
        || ChronoUnit.DAYS.between(request.checkIn(), request.checkOut())
            > properties.maxStayNights()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid booking dates.");
    }
    if (!"ACTIVE".equals(room.status())
        || room.blocked()
        || !"ACTIVE".equals(rate.typeStatus())
        || !"ACTIVE".equals(rate.propertyStatus())
        || request.guests() > rate.capacity()
        || bookings.hasConflict(room.id(), request.checkIn(), request.checkOut(), now, null)) {
      throw BookingRepository.conflict();
    }
    var quote = pricing.quote(rate.price(), request.checkIn(), request.checkOut());
    UUID id = UUID.randomUUID();
    bookings.insert(id, userId, request, room, quote, now.plus(properties.holdDuration()));
    return bookings.detail(id, userId, null);
  }

  @PreAuthorize("isAuthenticated() && #userId == authentication.principal.id")
  public BookingViews.Page mine(UUID userId, @NotNull @Valid BookingFilter filter) {
    return bookings.list(userId, null, filter);
  }

  @PreAuthorize("isAuthenticated() && #userId == authentication.principal.id")
  public BookingViews.Detail mine(UUID userId, UUID id) {
    return bookings.detail(id, userId, null);
  }

  @PreAuthorize("@propertyAccess.canManage(authentication, #propertyId)")
  public BookingViews.Page managed(UUID propertyId, @NotNull @Valid BookingFilter filter) {
    return bookings.list(null, propertyId, filter);
  }

  @PreAuthorize("@propertyAccess.canManage(authentication, #propertyId)")
  public BookingViews.Detail managed(UUID propertyId, UUID id) {
    return bookings.detail(id, null, propertyId);
  }

  @PreAuthorize("hasRole('ADMIN')")
  public BookingViews.Page all(UUID propertyId, @NotNull @Valid BookingFilter filter) {
    return bookings.list(null, propertyId, filter);
  }

  @PreAuthorize("hasRole('ADMIN')")
  public BookingViews.Detail adminDetail(UUID id) {
    return bookings.detail(id, null, null);
  }
}
