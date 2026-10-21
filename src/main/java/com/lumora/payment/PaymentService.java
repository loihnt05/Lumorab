package com.lumora.payment;

import static com.lumora.property.PropertyService.notFound;

import com.lumora.audit.AuditService;
import com.lumora.authorization.PropertyAccess;
import com.lumora.booking.BookingConfirmationService;
import com.lumora.booking.BookingRepository;
import com.lumora.booking.BookingStatus;
import com.lumora.identity.Role;
import com.lumora.identity.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
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
public class PaymentService {
  public static final String VIETQR_PROVIDER = "VIETQR";

  private final PaymentRepository payments;
  private final BookingRepository bookings;
  private final BookingConfirmationService confirmation;
  private final PaymentGateway gateway;
  private final PaymentProperties properties;
  private final PropertyAccess propertyAccess;
  private final AuditService audits;
  private final Clock clock;

  @Transactional(isolation = Isolation.READ_COMMITTED)
  @PreAuthorize("hasRole('USER') && #userId == authentication.principal.id")
  public PaymentViews.Detail create(UUID userId, UUID bookingId) {
    return create(userId, bookingId, null);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  @PreAuthorize("hasRole('USER') && #userId == authentication.principal.id")
  public PaymentViews.Detail create(UUID userId, UUID bookingId, String requestedProvider) {
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
    String provider = resolveProvider(requestedProvider);
    String externalRef;
    if (VIETQR_PROVIDER.equals(provider)) {
      externalRef = "VQR-ATTEMPT-" + id.toString().replace("-", "").toUpperCase(Locale.ROOT);
    } else {
      var attempt =
          gateway.createAttempt(
              id, paymentCode, bookingId, freshHeader.totalAmount(), freshHeader.currency());
      provider = attempt.provider();
      externalRef = attempt.externalReference();
    }
    payments.insert(
        id,
        bookingId,
        paymentCode,
        provider,
        freshHeader.totalAmount(),
        freshHeader.currency(),
        externalRef,
        now);
    return payments.detail(id);
  }

  private String resolveProvider(String requestedProvider) {
    String candidate =
        requestedProvider == null || requestedProvider.isBlank()
            ? properties.defaultProvider()
            : requestedProvider.trim().toUpperCase(Locale.ROOT);
    if (!MockPaymentGateway.PROVIDER.equals(candidate) && !VIETQR_PROVIDER.equals(candidate)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported payment provider.");
    }
    if (MockPaymentGateway.PROVIDER.equals(candidate)) {
      requireMockEnabled();
    }
    return candidate;
  }

  @PreAuthorize("isAuthenticated()")
  public List<PaymentViews.Detail> listForBooking(CurrentUser user, UUID bookingId) {
    var header = payments.findBookingHeader(bookingId);
    authorizeBookingOrPaymentRead(user, header);
    return payments.listByBooking(bookingId);
  }

  @PreAuthorize("isAuthenticated()")
  public PaymentViews.PaymentQr paymentQr(CurrentUser user, UUID paymentId) {
    var header = payments.findBookingHeaderByPayment(paymentId);
    authorizeBookingOrPaymentRead(user, header);
    return payments.paymentQr(paymentId);
  }

  private void authorizeBookingOrPaymentRead(
      CurrentUser user, PaymentRepository.BookingOwnerHeader header) {
    if (user.role() == Role.ADMIN) return;
    if (user.role() == Role.MANAGER) {
      if (!propertyAccess.managerHasProperty(user.id(), header.propertyId())
          && !header.userId().equals(user.id())) {
        throw new AccessDeniedException("Forbidden");
      }
      return;
    }
    if (!header.userId().equals(user.id())) throw notFound();
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

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public PaymentViews.WebhookResult handleWebhook(
      String rawProvider, String signature, @NotNull @Valid PaymentViews.WebhookRequest request) {
    verifyWebhookSignature(signature, request);
    String provider = rawProvider == null ? "" : rawProvider.trim().toUpperCase(Locale.ROOT);
    if (!VIETQR_PROVIDER.equals(provider) && !MockPaymentGateway.PROVIDER.equals(provider)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported webhook provider.");
    }
    UUID paymentId = payments.findPaymentIdByCode(request.paymentCode().trim());
    var header = payments.findBookingHeaderByPayment(paymentId);

    // Lock rooms in UUID order, then booking, then payment.
    bookings.roomIds(header.bookingId()).forEach(bookings::lockRoom);
    var lockedBooking = bookings.lockBooking(header.bookingId());
    var freshHeader = payments.findBookingHeader(header.bookingId());
    var payment = payments.lock(paymentId);
    if (!payment.provider().equalsIgnoreCase(provider)) {
      throw BookingRepository.conflict();
    }

    var now = clock.instant();
    String eventId = request.eventId().trim();
    String eventStatus = request.status().trim().toUpperCase(Locale.ROOT);
    String summary =
        "paymentCode="
            + payment.paymentCode()
            + ",status="
            + eventStatus
            + ",tx="
            + request.transactionId().trim();

    boolean firstSeen =
        payments.insertWebhookEventIfAbsent(
            provider,
            eventId,
            paymentId,
            header.bookingId(),
            eventStatus,
            "PROCESSED",
            summary,
            now);
    if (!firstSeen) {
      return new PaymentViews.WebhookResult(eventId, provider, true, payments.detail(paymentId));
    }

    if (payment.amount().compareTo(request.amount()) != 0
        || !payment.currency().equalsIgnoreCase(request.currency().trim())
        || payment.amount().compareTo(freshHeader.totalAmount()) != 0) {
      throw BookingRepository.conflict();
    }

    if ("PAID".equals(eventStatus) || "SUCCESS".equals(eventStatus)) {
      if (payment.status() == PaymentStatus.PAID
          && lockedBooking.status() == BookingStatus.CONFIRMED) {
        return new PaymentViews.WebhookResult(eventId, provider, true, payments.detail(paymentId));
      }
      payment.status().requireTransitionTo(PaymentStatus.PAID);
      confirmation.confirm(header.bookingId());
      payments.markPaid(paymentId, request.transactionId().trim(), now);
    } else if ("FAILED".equals(eventStatus)) {
      if (payment.status() != PaymentStatus.FAILED) {
        payments.changeStatus(payment, PaymentStatus.FAILED);
      }
    } else if ("CANCELLED".equals(eventStatus)) {
      if (payment.status() != PaymentStatus.CANCELLED) {
        payments.changeStatus(payment, PaymentStatus.CANCELLED);
      }
    } else {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Unsupported webhook payment status.");
    }

    return new PaymentViews.WebhookResult(eventId, provider, false, payments.detail(paymentId));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  @PreAuthorize("hasRole('ADMIN')")
  public PaymentViews.ReconcileResult reconcileBookingPayment(
      UUID bookingId, PaymentViews.ReconcileRequest request) {
    UUID latestPaymentId = payments.findLatestPaymentIdForBooking(bookingId);
    return reconcilePayment(latestPaymentId, request);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  @PreAuthorize("hasRole('ADMIN')")
  public PaymentViews.ReconcileResult reconcilePayment(
      UUID paymentId, PaymentViews.ReconcileRequest request) {
    var header = payments.findBookingHeaderByPayment(paymentId);
    bookings.roomIds(header.bookingId()).forEach(bookings::lockRoom);
    var lockedBooking = bookings.lockBooking(header.bookingId());
    var payment = payments.lock(paymentId);
    var now = clock.instant();

    String requestedStatus =
        request != null && request.providerStatus() != null && !request.providerStatus().isBlank()
            ? request.providerStatus().trim().toUpperCase(Locale.ROOT)
            : null;
    String txId =
        request != null && request.transactionId() != null && !request.transactionId().isBlank()
            ? request.transactionId().trim()
            : (payment.transactionId() != null
                ? payment.transactionId()
                : "RECON-TX-" + paymentId.toString().replace("-", "").toUpperCase(Locale.ROOT));

    String actionTaken = "NO_OP";

    if ("PAID".equals(requestedStatus) || payment.status() == PaymentStatus.PAID) {
      if (lockedBooking.status() == BookingStatus.PENDING_PAYMENT) {
        confirmation.confirm(header.bookingId());
        actionTaken = "CONFIRMED_BOOKING_AND_PAYMENT";
      }
      if (payment.status() == PaymentStatus.PENDING) {
        payments.markPaid(paymentId, txId, now);
        actionTaken = "CONFIRMED_BOOKING_AND_PAYMENT";
      }
    } else if ("FAILED".equals(requestedStatus) && payment.status() == PaymentStatus.PENDING) {
      payments.changeStatus(payment, PaymentStatus.FAILED);
      actionTaken = "MARKED_PAYMENT_FAILED";
    } else if ((lockedBooking.status() == BookingStatus.EXPIRED
            || lockedBooking.status() == BookingStatus.CANCELLED)
        && payment.status() == PaymentStatus.PENDING) {
      payments.changeStatus(payment, PaymentStatus.CANCELLED);
      actionTaken = "CANCELLED_STALE_PENDING_PAYMENT";
    }

    var detail = payments.detail(paymentId);
    audits.record(
        "PAYMENT_RECONCILE",
        "PAYMENT",
        paymentId,
        Map.of(
            "bookingId",
            header.bookingId().toString(),
            "actionTaken",
            actionTaken,
            "paymentStatus",
            detail.status().name(),
            "bookingStatus",
            detail.bookingStatus().name()));
    return new PaymentViews.ReconcileResult(
        header.bookingId(), detail.bookingStatus(), detail, actionTaken);
  }

  public String signWebhookPayload(PaymentViews.WebhookRequest request) {
    return computeHmacSha256(canonicalWebhookPayload(request), properties.webhookSecret());
  }

  private void verifyWebhookSignature(String signature, PaymentViews.WebhookRequest request) {
    if (signature == null || signature.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing webhook signature.");
    }
    String expected = signWebhookPayload(request);
    byte[] expectedBytes = expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
    byte[] actualBytes = signature.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
    if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature.");
    }
  }

  private static String canonicalWebhookPayload(PaymentViews.WebhookRequest request) {
    BigDecimal normalizedAmount = request.amount().setScale(2, RoundingMode.HALF_UP);
    return String.join(
        "|",
        request.eventId().trim(),
        request.paymentCode().trim(),
        request.status().trim().toUpperCase(Locale.ROOT),
        normalizedAmount.toPlainString(),
        request.currency().trim().toUpperCase(Locale.ROOT),
        request.transactionId().trim());
  }

  private static String computeHmacSha256(String data, String secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Unable to compute HMAC-SHA256", e);
    }
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
