package com.lumora.booking;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/** Shared by search, transactional create and confirmation. SQL uses the booking alias b. */
public final class BookingInventoryPolicy {
  private BookingInventoryPolicy() {}

  public static final String OVERLAPPING_OCCUPANCY =
      """
      b.check_in_date < :checkOut AND b.check_out_date > :checkIn
      AND (b.status IN (:occupiedStatuses)
           OR (b.status=:pendingStatus AND b.expires_at > :now))
      """;

  public static MapSqlParameterSource parameters(
      LocalDate checkIn, LocalDate checkOut, Instant now) {
    return new MapSqlParameterSource()
        .addValue("checkIn", checkIn)
        .addValue("checkOut", checkOut)
        .addValue("occupiedStatuses", BookingStatus.occupiedStatuses())
        .addValue("pendingStatus", BookingStatus.PENDING_PAYMENT.name())
        .addValue("now", Timestamp.from(now));
  }
}
