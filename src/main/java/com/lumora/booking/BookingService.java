package com.lumora.booking;

import static com.lumora.property.PropertyService.notFound;

import com.lumora.audit.AuditService;
import com.lumora.authorization.PropertyAccess;
import com.lumora.pricing.PricingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
  private final PropertyAccess propertyAccess;
  private final AuditService audits;
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

  @Transactional(isolation = Isolation.READ_COMMITTED)
  @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
  public BookingViews.Detail checkIn(
      Authentication authentication, UUID id, UUID scopedPropertyId) {
    UUID propertyId = resolveManagedProperty(authentication, id, scopedPropertyId);
    bookings.roomIds(id).forEach(bookings::lockRoom);
    var booking = bookings.lockBooking(id);
    booking.status().requireTransitionTo(BookingStatus.CHECKED_IN);
    var now = clock.instant();
    LocalDate today = LocalDate.ofInstant(now, properties.zone());
    if (today.isBefore(booking.checkIn()) || !today.isBefore(booking.checkOut())) {
      throw BookingRepository.conflict();
    }
    bookings.markCheckedIn(booking, now);
    audits.record(
        "BOOKING_CHECK_IN",
        "BOOKING",
        id,
        Map.of(
            "propertyId",
            propertyId.toString(),
            "previousStatus",
            booking.status().name(),
            "newStatus",
            BookingStatus.CHECKED_IN.name()));
    return bookings.detail(id, null, propertyId);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
  public BookingViews.Detail checkOut(
      Authentication authentication, UUID id, UUID scopedPropertyId) {
    UUID propertyId = resolveManagedProperty(authentication, id, scopedPropertyId);
    bookings.roomIds(id).forEach(bookings::lockRoom);
    var booking = bookings.lockBooking(id);
    booking.status().requireTransitionTo(BookingStatus.CHECKED_OUT);
    bookings.markCheckedOut(booking, clock.instant());
    audits.record(
        "BOOKING_CHECK_OUT",
        "BOOKING",
        id,
        Map.of(
            "propertyId",
            propertyId.toString(),
            "previousStatus",
            booking.status().name(),
            "newStatus",
            BookingStatus.CHECKED_OUT.name()));
    return bookings.detail(id, null, propertyId);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
  public BookingViews.Detail complete(
      Authentication authentication, UUID id, UUID scopedPropertyId) {
    UUID propertyId = resolveManagedProperty(authentication, id, scopedPropertyId);
    bookings.roomIds(id).forEach(bookings::lockRoom);
    var booking = bookings.lockBooking(id);
    booking.status().requireTransitionTo(BookingStatus.COMPLETED);
    bookings.markCompleted(booking, clock.instant());
    audits.record(
        "BOOKING_COMPLETE",
        "BOOKING",
        id,
        Map.of(
            "propertyId",
            propertyId.toString(),
            "previousStatus",
            booking.status().name(),
            "newStatus",
            BookingStatus.COMPLETED.name()));
    return bookings.detail(id, null, propertyId);
  }

  private UUID resolveManagedProperty(
      Authentication authentication, UUID id, UUID scopedPropertyId) {
    if (scopedPropertyId != null && !propertyAccess.canManage(authentication, scopedPropertyId)) {
      throw new AccessDeniedException("Forbidden");
    }
    UUID propertyId = bookings.findPropertyId(id);
    if (scopedPropertyId != null && !scopedPropertyId.equals(propertyId)) {
      throw notFound();
    }
    if (!propertyAccess.canManage(authentication, propertyId)) {
      throw new AccessDeniedException("Forbidden");
    }
    return propertyId;
  }
}
