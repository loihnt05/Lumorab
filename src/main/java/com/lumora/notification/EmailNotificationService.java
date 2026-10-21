package com.lumora.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import tools.jackson.databind.json.JsonMapper;

@Service
@Validated
@RequiredArgsConstructor
public class EmailNotificationService {
  private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);
  private final NamedParameterJdbcTemplate jdbc;
  private final JsonMapper json;
  private final Clock clock;

  @Transactional
  public boolean enqueue(
      String idempotencyKey,
      UUID bookingId,
      String recipientEmail,
      String template,
      String subject,
      Map<String, Object> metadata) {
    if (recipientEmail == null || recipientEmail.isBlank()) {
      return false;
    }
    Instant now = clock.instant();
    String payload = serializeSafe(metadata);
    var params =
        new MapSqlParameterSource()
            .addValue("id", UUID.randomUUID())
            .addValue("idempotencyKey", idempotencyKey)
            .addValue("bookingId", bookingId)
            .addValue("recipientEmail", recipientEmail.trim())
            .addValue("template", template)
            .addValue("subject", subject)
            .addValue("payload", payload)
            .addValue("now", Timestamp.from(now));
    int inserted =
        jdbc.update(
            """
            INSERT INTO email_notifications(
              id, idempotency_key, booking_id, recipient_email, template, subject,
              payload, status, created_at, sent_at
            )
            VALUES (
              :id, :idempotencyKey, :bookingId, :recipientEmail, :template, :subject,
              :payload, 'SENT', :now, :now
            )
            ON CONFLICT (idempotency_key) DO NOTHING
            """,
            params);
    if (inserted == 1) {
      log.info(
          "Email notification dispatched template={} bookingId={} key={}",
          template,
          bookingId,
          idempotencyKey);
    }
    return inserted == 1;
  }

  @Transactional
  public void enqueueBookingConfirmed(UUID bookingId) {
    var recipient = findRecipient(bookingId);
    if (recipient == null) return;
    enqueue(
        "confirm_" + bookingId,
        bookingId,
        recipient.email(),
        "BOOKING_CONFIRMED",
        "[Lumora] Booking confirmed #" + recipient.bookingCode(),
        Map.of(
            "bookingId",
            bookingId.toString(),
            "bookingCode",
            recipient.bookingCode(),
            "totalAmount",
            recipient.totalAmount().toPlainString(),
            "currency",
            recipient.currency()));
  }

  @Transactional
  public void enqueueBookingCancelled(
      UUID bookingId, int eligiblePercent, BigDecimal refundAmount, String currency) {
    var recipient = findRecipient(bookingId);
    if (recipient == null) return;
    enqueue(
        "cancel_" + bookingId,
        bookingId,
        recipient.email(),
        "BOOKING_CANCELLED",
        "[Lumora] Booking cancelled #" + recipient.bookingCode(),
        Map.of(
            "bookingId",
            bookingId.toString(),
            "bookingCode",
            recipient.bookingCode(),
            "eligiblePercent",
            eligiblePercent,
            "refundAmount",
            refundAmount.toPlainString(),
            "currency",
            currency));
  }

  @Transactional
  public void enqueueRefundUpdated(
      UUID bookingId, UUID refundId, BigDecimal refundAmount, String currency, String status) {
    var recipient = findRecipient(bookingId);
    if (recipient == null) return;
    enqueue(
        "refund_" + refundId + "_" + status,
        bookingId,
        recipient.email(),
        "REFUND_UPDATED",
        "[Lumora] Refund update #" + recipient.bookingCode(),
        Map.of(
            "bookingId",
            bookingId.toString(),
            "refundId",
            refundId.toString(),
            "refundAmount",
            refundAmount.toPlainString(),
            "currency",
            currency,
            "status",
            status));
  }

  @PreAuthorize("hasRole('ADMIN')")
  @Transactional(readOnly = true)
  public EmailNotificationViews.Page list(@NotNull @Valid EmailNotificationViews.Filter filter) {
    var params = new MapSqlParameterSource();
    String where = "1=1";
    if (filter.bookingId() != null) {
      where += " AND e.booking_id = :bookingId";
      params.addValue("bookingId", filter.bookingId());
    }
    if (filter.template() != null) {
      where += " AND e.template = :template";
      params.addValue("template", filter.template());
    }
    long total =
        jdbc.queryForObject(
            "SELECT count(*) FROM email_notifications e WHERE " + where, params, Long.class);
    params
        .addValue("limit", filter.size())
        .addValue("offset", (long) filter.page() * filter.size());
    var items =
        jdbc.query(
            """
            SELECT e.* FROM email_notifications e
            WHERE %s
            ORDER BY e.created_at DESC, e.id DESC
            LIMIT :limit OFFSET :offset
            """
                .formatted(where),
            params,
            (rs, n) -> mapEntry(rs));
    return new EmailNotificationViews.Page(items, filter.page(), filter.size(), total);
  }

  private record RecipientInfo(
      String bookingCode, String email, BigDecimal totalAmount, String currency) {}

  private RecipientInfo findRecipient(UUID bookingId) {
    return jdbc
        .query(
            """
            SELECT b.booking_code, b.total_amount, b.currency,
                   COALESCE(g.email, u.email) AS recipient_email
            FROM bookings b
            LEFT JOIN users u ON u.id = b.user_id
            LEFT JOIN booking_guests g ON g.booking_id = b.id AND g.is_primary_guest = true
            WHERE b.id = :id
            """,
            Map.of("id", bookingId),
            (rs, n) ->
                new RecipientInfo(
                    rs.getString("booking_code"),
                    rs.getString("recipient_email"),
                    rs.getBigDecimal("total_amount"),
                    rs.getString("currency")))
        .stream()
        .findFirst()
        .filter(r -> r.email() != null && !r.email().isBlank())
        .orElse(null);
  }

  private String serializeSafe(Map<String, Object> metadata) {
    if (metadata == null || metadata.isEmpty()) return null;
    Map<String, Object> sanitized = new LinkedHashMap<>();
    for (var entry : metadata.entrySet()) {
      String key = entry.getKey();
      if (key == null) continue;
      if (isSensitiveKey(key)) {
        sanitized.put(key, "[REDACTED]");
      } else {
        sanitized.put(key, entry.getValue());
      }
    }
    return json.writeValueAsString(sanitized);
  }

  private static boolean isSensitiveKey(String key) {
    String normalized = key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    return normalized.contains("codevalue")
        || normalized.contains("accesscode")
        || normalized.contains("secret")
        || normalized.contains("password")
        || normalized.contains("token");
  }

  private EmailNotificationViews.Entry mapEntry(ResultSet rs) throws SQLException {
    return new EmailNotificationViews.Entry(
        rs.getObject("id", UUID.class),
        rs.getString("idempotency_key"),
        rs.getObject("booking_id", UUID.class),
        rs.getString("recipient_email"),
        rs.getString("template"),
        rs.getString("subject"),
        rs.getString("payload"),
        rs.getString("status"),
        instant(rs, "created_at"),
        instant(rs, "sent_at"));
  }

  private Instant instant(ResultSet rs, String field) throws SQLException {
    var ts = rs.getTimestamp(field);
    return ts == null ? null : ts.toInstant();
  }
}
