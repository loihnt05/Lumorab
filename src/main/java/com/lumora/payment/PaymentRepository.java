package com.lumora.payment;

import static com.lumora.property.PropertyService.notFound;

import com.lumora.booking.BookingRepository;
import com.lumora.booking.BookingStatus;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PaymentRepository {
  public static final String PAYMENT_QR_PREFIX = "VIETQR|";

  private final NamedParameterJdbcTemplate jdbc;

  public record BookingOwnerHeader(
      UUID bookingId,
      UUID userId,
      UUID propertyId,
      BookingStatus status,
      BigDecimal totalAmount,
      String currency,
      Instant expiresAt) {}

  public record LockedPayment(
      UUID id,
      UUID bookingId,
      String paymentCode,
      String provider,
      BigDecimal amount,
      String currency,
      PaymentStatus status,
      String transactionId,
      String qrPayload,
      Instant paidAt) {}

  public static String buildQrPayload(
      String paymentCode, BigDecimal amount, String currency, String provider) {
    return PAYMENT_QR_PREFIX
        + "PROVIDER="
        + provider
        + "|BANK=970436|ACC=0123456789|AMOUNT="
        + amount.toPlainString()
        + "|CCY="
        + currency
        + "|REF="
        + paymentCode;
  }

  public BookingOwnerHeader findBookingHeader(UUID bookingId) {
    return jdbc
        .query(
            """
            SELECT id, user_id, property_id, status, total_amount, currency, expires_at
            FROM bookings WHERE id = :id
            """,
            Map.of("id", bookingId),
            (rs, n) ->
                new BookingOwnerHeader(
                    rs.getObject("id", UUID.class),
                    rs.getObject("user_id", UUID.class),
                    rs.getObject("property_id", UUID.class),
                    BookingStatus.valueOf(rs.getString("status")),
                    rs.getBigDecimal("total_amount"),
                    rs.getString("currency"),
                    instant(rs, "expires_at")))
        .stream()
        .findFirst()
        .orElseThrow(com.lumora.property.PropertyService::notFound);
  }

  public BookingOwnerHeader findBookingHeaderByPayment(UUID paymentId) {
    return jdbc
        .query(
            """
            SELECT b.id, b.user_id, b.property_id, b.status, b.total_amount, b.currency, b.expires_at
            FROM payments p JOIN bookings b ON b.id = p.booking_id
            WHERE p.id = :paymentId
            """,
            Map.of("paymentId", paymentId),
            (rs, n) ->
                new BookingOwnerHeader(
                    rs.getObject("id", UUID.class),
                    rs.getObject("user_id", UUID.class),
                    rs.getObject("property_id", UUID.class),
                    BookingStatus.valueOf(rs.getString("status")),
                    rs.getBigDecimal("total_amount"),
                    rs.getString("currency"),
                    instant(rs, "expires_at")))
        .stream()
        .findFirst()
        .orElseThrow(com.lumora.property.PropertyService::notFound);
  }

  public UUID findPaymentIdByCode(String paymentCode) {
    return jdbc
        .queryForList(
            "SELECT id FROM payments WHERE payment_code = :code",
            Map.of("code", paymentCode),
            UUID.class)
        .stream()
        .findFirst()
        .orElseThrow(com.lumora.property.PropertyService::notFound);
  }

  public UUID findLatestPaymentIdForBooking(UUID bookingId) {
    return jdbc
        .queryForList(
            """
            SELECT id FROM payments
            WHERE booking_id = :bookingId
            ORDER BY created_at DESC, id DESC LIMIT 1
            """,
            Map.of("bookingId", bookingId),
            UUID.class)
        .stream()
        .findFirst()
        .orElseThrow(com.lumora.property.PropertyService::notFound);
  }

  public LockedPayment lock(UUID id) {
    return jdbc
        .query(
            "SELECT * FROM payments WHERE id = :id FOR UPDATE",
            Map.of("id", id),
            (rs, n) ->
                new LockedPayment(
                    rs.getObject("id", UUID.class),
                    rs.getObject("booking_id", UUID.class),
                    rs.getString("payment_code"),
                    rs.getString("provider"),
                    rs.getBigDecimal("amount"),
                    rs.getString("currency"),
                    PaymentStatus.valueOf(rs.getString("status")),
                    rs.getString("transaction_id"),
                    rs.getString("qr_payload"),
                    instant(rs, "paid_at")))
        .stream()
        .findFirst()
        .orElseThrow(com.lumora.property.PropertyService::notFound);
  }

  public void insert(
      UUID id,
      UUID bookingId,
      String paymentCode,
      String provider,
      BigDecimal amount,
      String currency,
      String transactionId,
      Instant now) {
    String qrPayload = buildQrPayload(paymentCode, amount, currency, provider);
    var params =
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("bookingId", bookingId)
            .addValue("paymentCode", paymentCode)
            .addValue("provider", provider)
            .addValue("amount", amount)
            .addValue("currency", currency)
            .addValue("transactionId", transactionId)
            .addValue("qrPayload", qrPayload)
            .addValue("now", Timestamp.from(now));
    jdbc.update(
        """
        INSERT INTO payments(id, booking_id, payment_code, provider, amount, currency,
          status, transaction_id, qr_payload, created_at, updated_at)
        VALUES (:id, :bookingId, :paymentCode, :provider, :amount, :currency,
          'PENDING', :transactionId, :qrPayload, :now, :now)
        """,
        params);
  }

  public void markPaid(UUID id, String transactionId, Instant paidAt) {
    PaymentStatus.PENDING.requireTransitionTo(PaymentStatus.PAID);
    int updated =
        jdbc.update(
            """
            UPDATE payments
            SET status = 'PAID', transaction_id = :txId, paid_at = :paidAt
            WHERE id = :id AND status = 'PENDING'
            """,
            new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("txId", transactionId)
                .addValue("paidAt", Timestamp.from(paidAt)));
    if (updated != 1) throw BookingRepository.conflict();
  }

  public void changeStatus(LockedPayment payment, PaymentStatus next) {
    payment.status().requireTransitionTo(next);
    int updated =
        jdbc.update(
            """
            UPDATE payments SET status = :next
            WHERE id = :id AND status = :previous
            """,
            Map.of(
                "id", payment.id(),
                "previous", payment.status().name(),
                "next", next.name()));
    if (updated != 1) throw BookingRepository.conflict();
  }

  public boolean insertWebhookEventIfAbsent(
      String provider,
      String eventId,
      UUID paymentId,
      UUID bookingId,
      String eventType,
      String outcome,
      String payloadSummary,
      Instant now) {
    int inserted =
        jdbc.update(
            """
            INSERT INTO payment_webhook_events(
              id, provider, event_id, payment_id, booking_id,
              event_type, outcome, payload_summary, received_at
            ) VALUES (
              :id, :provider, :eventId, :paymentId, :bookingId,
              :eventType, :outcome, :payloadSummary, :now
            ) ON CONFLICT (provider, event_id) DO NOTHING
            """,
            new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("provider", provider)
                .addValue("eventId", eventId)
                .addValue("paymentId", paymentId)
                .addValue("bookingId", bookingId)
                .addValue("eventType", eventType)
                .addValue("outcome", outcome)
                .addValue("payloadSummary", payloadSummary)
                .addValue("now", Timestamp.from(now)));
    return inserted == 1;
  }

  public PaymentViews.Detail detail(UUID id) {
    return jdbc
        .query(
            """
            SELECT p.*, b.booking_code, b.status AS booking_status
            FROM payments p JOIN bookings b ON b.id = p.booking_id
            WHERE p.id = :id
            """,
            Map.of("id", id),
            (rs, n) -> mapDetail(rs))
        .stream()
        .findFirst()
        .orElseThrow(() -> notFound());
  }

  public PaymentViews.PaymentQr paymentQr(UUID id) {
    return jdbc
        .query(
            "SELECT * FROM payments WHERE id = :id",
            Map.of("id", id),
            (rs, n) -> {
              String code = rs.getString("payment_code");
              BigDecimal amount = rs.getBigDecimal("amount");
              String currency = rs.getString("currency");
              String provider = rs.getString("provider");
              String qrPayload = rs.getString("qr_payload");
              if (qrPayload == null || qrPayload.isBlank()) {
                qrPayload = buildQrPayload(code, amount, currency, provider);
              }
              return new PaymentViews.PaymentQr(
                  rs.getObject("id", UUID.class),
                  rs.getObject("booking_id", UUID.class),
                  code,
                  provider,
                  amount,
                  currency,
                  PaymentStatus.valueOf(rs.getString("status")),
                  qrPayload,
                  "PAYMENT_QR");
            })
        .stream()
        .findFirst()
        .orElseThrow(() -> notFound());
  }

  public List<PaymentViews.Detail> listByBooking(UUID bookingId) {
    return jdbc.query(
        """
        SELECT p.*, b.booking_code, b.status AS booking_status
        FROM payments p JOIN bookings b ON b.id = p.booking_id
        WHERE p.booking_id = :bookingId
        ORDER BY p.created_at DESC, p.id DESC
        """,
        Map.of("bookingId", bookingId),
        (rs, n) -> mapDetail(rs));
  }

  private PaymentViews.Detail mapDetail(ResultSet rs) throws SQLException {
    return new PaymentViews.Detail(
        rs.getObject("id", UUID.class),
        rs.getObject("booking_id", UUID.class),
        rs.getString("booking_code"),
        BookingStatus.valueOf(rs.getString("booking_status")),
        rs.getString("payment_code"),
        rs.getString("provider"),
        rs.getBigDecimal("amount"),
        rs.getString("currency"),
        PaymentStatus.valueOf(rs.getString("status")),
        rs.getString("transaction_id"),
        instant(rs, "paid_at"),
        instant(rs, "created_at"),
        instant(rs, "updated_at"));
  }

  private Instant instant(ResultSet rs, String field) throws SQLException {
    var value = rs.getTimestamp(field);
    return value == null ? null : value.toInstant();
  }
}
