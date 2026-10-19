package com.lumora.payment;

import static com.lumora.property.PropertyService.notFound;

import com.lumora.authorization.PropertyAccess;
import com.lumora.booking.BookingConfirmationService;
import com.lumora.booking.BookingRepository;
import com.lumora.booking.BookingStatus;
import com.lumora.identity.Role;
import com.lumora.identity.security.CurrentUser;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {
  private final PaymentRepository payments;
  private final BookingRepository bookings;
  private final BookingConfirmationService confirmation;
  private final PaymentGateway gateway;
  private final PaymentProperties properties;
  private final PropertyAccess propertyAccess;
  private final Clock clock;

  @Transactional(isolation = Isolation.READ_COMMITTED)
  @PreAuthorize("hasRole('USER') && #userId == authentication.principal.id")
  public PaymentViews.Detail create(UUID userId, UUID bookingId) {
    var header = payments.findBookingHeader(bookingId);
    if (!header.userId().equals(userId)) throw notFound();
    // Preserve room-before-booking lock order across all booking/payment mutations.
    bookings.roomIds(bookingId).forEach(bookings::lockRoom);
    var lockedBooking = bookings.lockBooking(bookingId);
    var freshHeader = payments.findBookingHeader(bookingId);
    var now = clock.instant();
    if (lockedBooking.status() != BookingStatus.PENDING_PAYMENT
        || lockedBooking.expiresAt() == null
        || !lockedBooking.expiresAt().isAfter(now)) {
      throw BookingRepository.conflict();
    }
    UUID id = UUID.randomUUID();
    String paymentCode = "PAY-" + id.toString().replace("-", "").toUpperCase(Locale.ROOT);
    var attempt =
        gateway.createAttempt(
            id, paymentCode, bookingId, freshHeader.totalAmount(), freshHeader.currency());
    payments.insert(
        id,
        bookingId,
        paymentCode,
        attempt.provider(),
        freshHeader.totalAmount(),
        freshHeader.currency(),
        attempt.externalReference(),
        now);
    return payments.detail(id);
  }

  @PreAuthorize("isAuthenticated()")
  public List<PaymentViews.Detail> listForBooking(CurrentUser user, UUID bookingId) {
    var header = payments.findBookingHeader(bookingId);
    if (user.role() == Role.ADMIN) {
      return payments.listByBooking(bookingId);
    }
    if (user.role() == Role.MANAGER) {
      if (!propertyAccess.managerHasProperty(user.id(), header.propertyId())) {
        throw new AccessDeniedException("Forbidden");
      }
      return payments.listByBooking(bookingId);
    }
    if (!header.userId().equals(user.id())) throw notFound();
    return payments.listByBooking(bookingId);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  @PreAuthorize("isAuthenticated()")
  public PaymentViews.Detail mockSuccess(CurrentUser user, UUID paymentId) {
    requireMockEnabled();
    var header = payments.findBookingHeaderByPayment(paymentId);
    if (user.role() != Role.ADMIN && !header.userId().equals(user.id())) throw notFound();
    // Lock rooms in UUID order, then booking, then payment row.
    bookings.roomIds(header.bookingId()).forEach(bookings::lockRoom);
    var lockedBooking = bookings.lockBooking(header.bookingId());
    var freshHeader = payments.findBookingHeader(header.bookingId());
    var payment = payments.lock(paymentId);
    if (!MockPaymentGateway.PROVIDER.equals(payment.provider())) {
      throw BookingRepository.conflict();
    }
    if (payment.status() == PaymentStatus.PAID
        && lockedBooking.status() == BookingStatus.CONFIRMED) {
      return payments.detail(paymentId);
    }
    payment.status().requireTransitionTo(PaymentStatus.PAID);
    if (payment.amount().compareTo(freshHeader.totalAmount()) != 0
        || !payment.currency().equals(freshHeader.currency())) {
      throw BookingRepository.conflict();
    }
    confirmation.confirm(header.bookingId());
    String txId = "MOCK-TX-" + paymentId.toString().replace("-", "").toUpperCase(Locale.ROOT);
    payments.markPaid(paymentId, txId, clock.instant());
    return payments.detail(paymentId);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  @PreAuthorize("isAuthenticated()")
  public PaymentViews.Detail mockFail(CurrentUser user, UUID paymentId) {
    return transitionMockFailureOrCancel(user, paymentId, PaymentStatus.FAILED);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  @PreAuthorize("isAuthenticated()")
  public PaymentViews.Detail mockCancel(CurrentUser user, UUID paymentId) {
    return transitionMockFailureOrCancel(user, paymentId, PaymentStatus.CANCELLED);
  }

  private PaymentViews.Detail transitionMockFailureOrCancel(
      CurrentUser user, UUID paymentId, PaymentStatus target) {
    requireMockEnabled();
    var header = payments.findBookingHeaderByPayment(paymentId);
    if (user.role() != Role.ADMIN && !header.userId().equals(user.id())) throw notFound();
    bookings.roomIds(header.bookingId()).forEach(bookings::lockRoom);
    bookings.lockBooking(header.bookingId());
    var payment = payments.lock(paymentId);
    if (!MockPaymentGateway.PROVIDER.equals(payment.provider())) {
      throw BookingRepository.conflict();
    }
    if (payment.status() == target) {
      return payments.detail(paymentId);
    }
    payments.changeStatus(payment, target);
    return payments.detail(paymentId);
  }

  private void requireMockEnabled() {
    if (!Boolean.TRUE.equals(properties.mockEnabled())) throw notFound();
  }
}
