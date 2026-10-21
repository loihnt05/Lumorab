package com.lumora.cancellation;

import static com.lumora.property.PropertyService.notFound;

import com.lumora.audit.AuditService;
import com.lumora.authorization.PropertyAccess;
import com.lumora.booking.BookingRepository;
import com.lumora.booking.BookingStatus;
import com.lumora.identity.Role;
import com.lumora.identity.security.CurrentUser;
import com.lumora.notification.EmailNotificationService;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CancellationService {
  private final NamedParameterJdbcTemplate jdbc;
  private final BookingRepository bookings;
  private final CancellationPolicyService policyService;
  private final PropertyAccess propertyAccess;
  private final AuditService audits;
  private final EmailNotificationService notifications;
  private final Clock clock;

  private record BookingRow(
      UUID id,
      String bookingCode,
      UUID userId,
      UUID propertyId,
      BookingStatus status,
      LocalDate checkIn,
      BigDecimal totalAmount,
      String currency) {}

  private record PaidPaymentRow(UUID id, BigDecimal amount, String currency) {}

  @PreAuthorize("isAuthenticated()")
  public CancellationViews.PolicyQuote previewPolicy(CurrentUser user, UUID bookingId) {
    var row = findBookingRow(bookingId);
    authorizeBookingAccess(user, row);
    var paid = findPaidPayment(bookingId);
    return policyService.evaluate(
        row.id(),
        row.bookingCode(),
        row.status(),
        row.checkIn(),
        row.totalAmount(),
        paid == null ? BigDecimal.ZERO : paid.amount(),
        row.currency(),
        clock.instant());
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  @PreAuthorize("isAuthenticated()")
  public CancellationViews.CancelResult cancel(
      CurrentUser user, UUID bookingId, CancellationViews.CancelRequest request) {
    var initialRow = findBookingRow(bookingId);
    authorizeBookingAccess(user, initialRow);
    // Lock rooms in UUID order before locking booking row.
    bookings.roomIds(bookingId).forEach(bookings::lockRoom);
    var lockedBooking = bookings.lockBooking(bookingId);
    var freshRow = findBookingRow(bookingId);
    var paidPayment = findPaidPayment(bookingId);
    var now = clock.instant();

    var quote =
        policyService.evaluate(
            freshRow.id(),
            freshRow.bookingCode(),
            lockedBooking.status(),
            lockedBooking.checkIn(),
            freshRow.totalAmount(),
            paidPayment == null ? BigDecimal.ZERO : paidPayment.amount(),
            freshRow.currency(),
            now);
    if (!quote.canCancel()) {
      throw BookingRepository.conflict();
    }

    String reason =
        request != null && request.reason() != null && !request.reason().isBlank()
            ? request.reason().trim()
            : "Cancelled by " + user.role().name().toLowerCase(Locale.ROOT);

    bookings.markCancelled(lockedBooking, user.id(), reason, now);
    cancelPendingPayments(bookingId, now);

    CancellationViews.RefundDetail refundDetail = null;
    if (paidPayment != null) {
      refundDetail = upsertAndProcessRefund(bookingId, paidPayment, quote, reason, now);
      notifications.enqueueRefundUpdated(
          bookingId,
          refundDetail.id(),
          refundDetail.refundAmount(),
          refundDetail.currency(),
          refundDetail.status());
    }

    notifications.enqueueBookingCancelled(
        bookingId, quote.refundPercentage(), quote.refundableAmount(), quote.currency());

    audits.record(
        "BOOKING_CANCEL",
        "BOOKING",
        bookingId,
        Map.of(
            "propertyId",
            freshRow.propertyId().toString(),
            "previousStatus",
            lockedBooking.status().name(),
            "newStatus",
            BookingStatus.CANCELLED.name(),
            "policyTier",
            quote.policyTier(),
            "refundPercentage",
            quote.refundPercentage(),
            "refundableAmount",
            quote.refundableAmount().toPlainString()));

    var updatedBooking = bookings.detail(bookingId, null, null);
    return new CancellationViews.CancelResult(updatedBooking, quote, refundDetail);
  }

  @PreAuthorize("isAuthenticated()")
  public CancellationViews.RefundDetail getRefund(CurrentUser user, UUID bookingId) {
    var row = findBookingRow(bookingId);
    authorizeBookingAccess(user, row);
    return findRefundByBooking(bookingId);
  }

  private void authorizeBookingAccess(CurrentUser user, BookingRow row) {
    if (user.role() == Role.ADMIN) return;
    if (user.role() == Role.MANAGER) {
      if (!propertyAccess.managerHasProperty(user.id(), row.propertyId())
          && !row.userId().equals(user.id())) {
        throw new AccessDeniedException("Forbidden");
      }
      return;
    }
    if (!row.userId().equals(user.id())) {
      throw notFound();
    }
  }

  private BookingRow findBookingRow(UUID bookingId) {
    return jdbc
        .query(
            """
            SELECT id, booking_code, user_id, property_id, status, check_in_date, total_amount, currency
            FROM bookings WHERE id = :id
            """,
            Map.of("id", bookingId),
            (rs, n) ->
                new BookingRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("booking_code"),
                    rs.getObject("user_id", UUID.class),
                    rs.getObject("property_id", UUID.class),
                    BookingStatus.valueOf(rs.getString("status")),
                    rs.getObject("check_in_date", LocalDate.class),
                    rs.getBigDecimal("total_amount"),
                    rs.getString("currency")))
        .stream()
        .findFirst()
        .orElseThrow(() -> notFound());
  }

  private PaidPaymentRow findPaidPayment(UUID bookingId) {
    return jdbc
        .query(
            """
            SELECT id, amount, currency FROM payments
            WHERE booking_id = :bookingId AND status IN ('PAID', 'REFUNDED')
            ORDER BY created_at DESC LIMIT 1
            """,
            Map.of("bookingId", bookingId),
            (rs, n) ->
                new PaidPaymentRow(
                    rs.getObject("id", UUID.class),
                    rs.getBigDecimal("amount"),
                    rs.getString("currency")))
        .stream()
        .findFirst()
        .orElse(null);
  }

  private void cancelPendingPayments(UUID bookingId, Instant now) {
    jdbc.update(
        """
        UPDATE payments
        SET status = 'CANCELLED', updated_at = :now
        WHERE booking_id = :bookingId AND status = 'PENDING'
        """,
        new MapSqlParameterSource("bookingId", bookingId).addValue("now", Timestamp.from(now)));
  }

  private CancellationViews.RefundDetail upsertAndProcessRefund(
      UUID bookingId,
      PaidPaymentRow paidPayment,
      CancellationViews.PolicyQuote quote,
      String reason,
      Instant now) {
    String refundStatus =
        quote.refundableAmount().compareTo(BigDecimal.ZERO) > 0 ? "REFUNDED" : "NO_REFUND";
    String refundCode = "RFD-" + bookingId.toString().replace("-", "").toUpperCase(Locale.ROOT);
    jdbc.update(
        """
        INSERT INTO refunds(
          id, booking_id, payment_id, refund_code, refund_amount, fee_amount,
          refund_percentage, currency, policy_tier, status, reason, processed_at, created_at, updated_at
        ) VALUES (
          :id, :bookingId, :paymentId, :refundCode, :refundAmount, :feeAmount,
          :refundPercentage, :currency, :policyTier, :status, :reason, :now, :now, :now
        ) ON CONFLICT (booking_id) DO NOTHING
        """,
        new MapSqlParameterSource()
            .addValue("id", UUID.randomUUID())
            .addValue("bookingId", bookingId)
            .addValue("paymentId", paidPayment.id())
            .addValue("refundCode", refundCode)
            .addValue("refundAmount", quote.refundableAmount())
            .addValue("feeAmount", quote.cancellationFeeAmount())
            .addValue("refundPercentage", quote.refundPercentage())
            .addValue("currency", quote.currency())
            .addValue("policyTier", quote.policyTier())
            .addValue("status", refundStatus)
            .addValue("reason", reason)
            .addValue("now", Timestamp.from(now)));
    if ("REFUNDED".equals(refundStatus)) {
      jdbc.update(
          """
          UPDATE payments
          SET status = 'REFUNDED', refunded_at = :now, updated_at = :now
          WHERE id = :paymentId AND status = 'PAID'
          """,
          new MapSqlParameterSource("paymentId", paidPayment.id())
              .addValue("now", Timestamp.from(now)));
    }
    return findRefundByBooking(bookingId);
  }

  private CancellationViews.RefundDetail findRefundByBooking(UUID bookingId) {
    return jdbc
        .query(
            "SELECT * FROM refunds WHERE booking_id = :bookingId",
            Map.of("bookingId", bookingId),
            (rs, n) -> mapRefund(rs))
        .stream()
        .findFirst()
        .orElseThrow(() -> notFound());
  }

  private CancellationViews.RefundDetail mapRefund(ResultSet rs) throws SQLException {
    var processed = rs.getTimestamp("processed_at");
    return new CancellationViews.RefundDetail(
        rs.getObject("id", UUID.class),
        rs.getObject("booking_id", UUID.class),
        rs.getObject("payment_id", UUID.class),
        rs.getString("refund_code"),
        rs.getBigDecimal("refund_amount"),
        rs.getBigDecimal("fee_amount"),
        rs.getInt("refund_percentage"),
        rs.getString("currency"),
        rs.getString("policy_tier"),
        rs.getString("status"),
        rs.getString("reason"),
        processed == null ? null : processed.toInstant(),
        rs.getTimestamp("created_at").toInstant());
  }
}
