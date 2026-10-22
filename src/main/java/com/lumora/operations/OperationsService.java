package com.lumora.operations;

import static com.lumora.property.PropertyService.notFound;

import com.lumora.booking.BookingInventoryPolicy;
import com.lumora.booking.BookingProperties;
import com.lumora.booking.BookingStatus;
import com.lumora.booking.BookingViews;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OperationsService {
  private final NamedParameterJdbcTemplate jdbc;
  private final BookingProperties bookingProperties;
  private final Clock clock;

  @PreAuthorize("@propertyAccess.canManage(authentication, #propertyId)")
  public OperationsViews.ManagerDashboard managerDashboard(
      UUID propertyId, LocalDate requestedDate) {
    var property =
        jdbc
            .query(
                "SELECT id, name, slug, status FROM properties WHERE id = :id",
                Map.of("id", propertyId),
                (rs, n) ->
                    new PropertyHeader(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("slug"),
                        rs.getString("status")))
            .stream()
            .findFirst()
            .orElseThrow(() -> notFound());

    Instant now = clock.instant();
    LocalDate date =
        requestedDate != null ? requestedDate : LocalDate.ofInstant(now, bookingProperties.zone());

    var p =
        BookingInventoryPolicy.parameters(date, date.plusDays(1), now)
            .addValue("propertyId", propertyId)
            .addValue("date", date);

    long totalRooms = count("SELECT count(*) FROM rooms WHERE property_id = :propertyId", p);
    long activeRooms =
        count(
            "SELECT count(*) FROM rooms WHERE property_id = :propertyId AND status = 'ACTIVE' AND NOT is_blocked",
            p);
    long blockedRooms =
        count("SELECT count(*) FROM rooms WHERE property_id = :propertyId AND is_blocked", p);
    long inactiveRooms =
        count(
            "SELECT count(*) FROM rooms WHERE property_id = :propertyId AND status = 'INACTIVE'",
            p);

    long occupiedRoomsToday =
        count(
            """
            SELECT count(DISTINCT br.room_id)
            FROM booking_rooms br
            JOIN bookings b ON b.id = br.booking_id
            WHERE b.property_id = :propertyId AND
            """
                + BookingInventoryPolicy.OVERLAPPING_OCCUPANCY,
            p);

    BigDecimal occupancyRate =
        activeRooms == 0
            ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.valueOf(occupiedRoomsToday * 100)
                .divide(BigDecimal.valueOf(activeRooms), 2, RoundingMode.HALF_UP);

    long bookingsToday =
        count(
            """
            SELECT count(*) FROM bookings b
            WHERE b.property_id = :propertyId AND
            """
                + BookingInventoryPolicy.OVERLAPPING_OCCUPANCY,
            p);

    long checkInsToday =
        count(
            """
            SELECT count(*) FROM bookings
            WHERE property_id = :propertyId AND status = 'CONFIRMED' AND check_in_date = :date
            """,
            p);
    long checkOutsToday =
        count(
            """
            SELECT count(*) FROM bookings
            WHERE property_id = :propertyId AND status = 'CHECKED_IN' AND check_out_date = :date
            """,
            p);
    long upcomingCheckIns =
        count(
            """
            SELECT count(*) FROM bookings
            WHERE property_id = :propertyId AND status = 'CONFIRMED' AND check_in_date >= :date
            """,
            p);
    long upcomingCheckOuts =
        count(
            """
            SELECT count(*) FROM bookings
            WHERE property_id = :propertyId AND status = 'CHECKED_IN' AND check_out_date >= :date
            """,
            p);
    long checkedInCount =
        count(
            """
            SELECT count(*) FROM bookings
            WHERE property_id = :propertyId AND status = 'CHECKED_IN'
            """,
            p);

    var upcomingCheckInBookings =
        jdbc.query(
            """
            SELECT b.*, p.name AS property_name
            FROM bookings b JOIN properties p ON p.id = b.property_id
            WHERE b.property_id = :propertyId AND b.status = 'CONFIRMED' AND b.check_in_date >= :date
            ORDER BY b.check_in_date ASC, b.created_at ASC, b.id ASC
            LIMIT 10
            """,
            p,
            (rs, n) -> mapBookingSummary(rs));

    var upcomingCheckOutBookings =
        jdbc.query(
            """
            SELECT b.*, p.name AS property_name
            FROM bookings b JOIN properties p ON p.id = b.property_id
            WHERE b.property_id = :propertyId AND b.status = 'CHECKED_IN' AND b.check_out_date >= :date
            ORDER BY b.check_out_date ASC, b.created_at ASC, b.id ASC
            LIMIT 10
            """,
            p,
            (rs, n) -> mapBookingSummary(rs));

    var recentBookings =
        jdbc.query(
            """
            SELECT b.*, p.name AS property_name
            FROM bookings b JOIN properties p ON p.id = b.property_id
            WHERE b.property_id = :propertyId
            ORDER BY b.created_at DESC, b.id DESC
            LIMIT 10
            """,
            p,
            (rs, n) -> mapBookingSummary(rs));

    return new OperationsViews.ManagerDashboard(
        property.id(),
        property.name(),
        property.slug(),
        property.status(),
        date,
        totalRooms,
        activeRooms,
        blockedRooms,
        inactiveRooms,
        occupiedRoomsToday,
        occupancyRate,
        bookingsToday,
        checkInsToday,
        checkOutsToday,
        upcomingCheckIns,
        upcomingCheckOuts,
        checkedInCount,
        upcomingCheckInBookings,
        upcomingCheckOutBookings,
        recentBookings);
  }

  @PreAuthorize("hasRole('ADMIN')")
  public OperationsViews.AdminMonitorSummary monitorSummary(
      UUID propertyId, LocalDate from, LocalDate to) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date range.");
    }
    if (propertyId != null) {
      requirePropertyExists(propertyId);
    }

    Instant now = clock.instant();
    LocalDate today = LocalDate.ofInstant(now, bookingProperties.zone());
    var params =
        BookingInventoryPolicy.parameters(today, today.plusDays(1), now)
            .addValue("propertyId", propertyId)
            .addValue("today", today);

    long totalUsers = count("SELECT count(*) FROM users", params);
    long totalRegularUsers = count("SELECT count(*) FROM users WHERE role = 'USER'", params);
    long totalManagers = count("SELECT count(*) FROM users WHERE role = 'MANAGER'", params);
    long totalAdmins = count("SELECT count(*) FROM users WHERE role = 'ADMIN'", params);

    String propFilter = propertyId == null ? "" : " AND id = :propertyId";
    String roomPropFilter = propertyId == null ? "" : " AND property_id = :propertyId";
    String bookingPropFilter = propertyId == null ? "" : " AND b.property_id = :propertyId";

    long totalProperties = count("SELECT count(*) FROM properties WHERE 1=1" + propFilter, params);
    long activeProperties =
        count("SELECT count(*) FROM properties WHERE status = 'ACTIVE'" + propFilter, params);

    long totalRooms = count("SELECT count(*) FROM rooms WHERE 1=1" + roomPropFilter, params);
    long activeRooms =
        count(
            "SELECT count(*) FROM rooms WHERE status = 'ACTIVE' AND NOT is_blocked"
                + roomPropFilter,
            params);
    long blockedRooms =
        count("SELECT count(*) FROM rooms WHERE is_blocked" + roomPropFilter, params);
    long inactiveRooms =
        count("SELECT count(*) FROM rooms WHERE status = 'INACTIVE'" + roomPropFilter, params);

    String bookingDateClause = buildBookingCreatedClause(from, to, params);
    Map<String, Long> bookingsByStatus = new LinkedHashMap<>();
    for (BookingStatus status : BookingStatus.values()) {
      bookingsByStatus.put(status.name(), 0L);
    }
    jdbc.query(
        "SELECT b.status, count(*) AS cnt FROM bookings b WHERE 1=1"
            + bookingPropFilter
            + bookingDateClause
            + " GROUP BY b.status",
        params,
        rs -> {
          bookingsByStatus.put(rs.getString("status"), rs.getLong("cnt"));
        });

    long bookingsToday =
        count(
            "SELECT count(*) FROM bookings b WHERE "
                + BookingInventoryPolicy.OVERLAPPING_OCCUPANCY
                + bookingPropFilter,
            params);

    long upcomingCheckIns =
        count(
            "SELECT count(*) FROM bookings b WHERE b.status = 'CONFIRMED' AND b.check_in_date >= :today"
                + bookingPropFilter,
            params);

    String paymentDateClause = buildPaymentPaidClause(from, to, params);
    BigDecimal simulatedRevenue =
        jdbc.queryForObject(
            """
            SELECT COALESCE(sum(p.amount), 0)
            FROM payments p JOIN bookings b ON b.id = p.booking_id
            WHERE p.status = 'PAID'
            """
                + bookingPropFilter
                + paymentDateClause,
            params,
            BigDecimal.class);
    long paidPaymentCount =
        count(
            """
            SELECT count(*)
            FROM payments p JOIN bookings b ON b.id = p.booking_id
            WHERE p.status = 'PAID'
            """
                + bookingPropFilter
                + paymentDateClause,
            params);

    var latestBookings =
        jdbc.query(
            """
            SELECT b.*, p.name AS property_name
            FROM bookings b JOIN properties p ON p.id = b.property_id
            WHERE 1=1
            """
                + bookingPropFilter
                + bookingDateClause
                + " ORDER BY b.created_at DESC, b.id DESC LIMIT 10",
            params,
            (rs, n) -> mapBookingSummary(rs));

    var blockedRoomList =
        jdbc.query(
            """
            SELECT r.id, r.property_id, p.name AS property_name, r.room_number, r.name, r.status
            FROM rooms r JOIN properties p ON p.id = r.property_id
            WHERE r.is_blocked
            """
                + (propertyId == null ? "" : " AND r.property_id = :propertyId")
                + " ORDER BY p.name ASC, r.room_number ASC, r.id ASC",
            params,
            (rs, n) ->
                new OperationsViews.BlockedRoomItem(
                    rs.getObject("id", UUID.class),
                    rs.getObject("property_id", UUID.class),
                    rs.getString("property_name"),
                    rs.getString("room_number"),
                    rs.getString("name"),
                    rs.getString("status")));

    var upcomingCheckInList =
        jdbc.query(
            """
            SELECT b.*, p.name AS property_name
            FROM bookings b JOIN properties p ON p.id = b.property_id
            WHERE b.status = 'CONFIRMED' AND b.check_in_date >= :today
            """
                + bookingPropFilter
                + " ORDER BY b.check_in_date ASC, b.created_at ASC, b.id ASC LIMIT 10",
            params,
            (rs, n) -> mapBookingSummary(rs));

    return new OperationsViews.AdminMonitorSummary(
        today,
        propertyId,
        from,
        to,
        totalUsers,
        totalRegularUsers,
        totalManagers,
        totalAdmins,
        totalProperties,
        activeProperties,
        totalRooms,
        activeRooms,
        blockedRooms,
        inactiveRooms,
        bookingsByStatus,
        bookingsToday,
        upcomingCheckIns,
        simulatedRevenue.setScale(2, RoundingMode.HALF_UP),
        paidPaymentCount,
        OperationsViews.CURRENCY,
        OperationsViews.SIMULATED_REVENUE_LABEL,
        latestBookings,
        blockedRoomList,
        upcomingCheckInList);
  }

  @PreAuthorize("hasRole('ADMIN')")
  public OperationsViews.RevenueReport monitorRevenue(
      LocalDate from, LocalDate to, UUID propertyId) {
    DateRange range = resolveRange(from, to);
    if (propertyId != null) {
      requirePropertyExists(propertyId);
    }
    var params =
        new MapSqlParameterSource()
            .addValue("propertyId", propertyId)
            .addValue("zone", bookingProperties.zone().getId());
    String bookingPropFilter = propertyId == null ? "" : " AND b.property_id = :propertyId";
    String paymentDateClause = buildPaymentPaidClause(range.from(), range.to(), params);

    Map<LocalDate, OperationsViews.DailyRevenuePoint> byDate = new LinkedHashMap<>();
    for (LocalDate d = range.from(); !d.isAfter(range.to()); d = d.plusDays(1)) {
      byDate.put(
          d,
          new OperationsViews.DailyRevenuePoint(
              d, 0L, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)));
    }

    jdbc.query(
        """
        SELECT (p.paid_at AT TIME ZONE :zone)::date AS paid_date,
               count(*) AS cnt,
               COALESCE(sum(p.amount), 0) AS total
        FROM payments p JOIN bookings b ON b.id = p.booking_id
        WHERE p.status = 'PAID'
        """
            + bookingPropFilter
            + paymentDateClause
            + " GROUP BY paid_date ORDER BY paid_date ASC",
        params,
        rs -> {
          LocalDate d = rs.getObject("paid_date", LocalDate.class);
          byDate.put(
              d,
              new OperationsViews.DailyRevenuePoint(
                  d,
                  rs.getLong("cnt"),
                  rs.getBigDecimal("total").setScale(2, RoundingMode.HALF_UP)));
        });

    BigDecimal totalRevenue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    long totalCount = 0L;
    for (var point : byDate.values()) {
      totalRevenue = totalRevenue.add(point.simulatedRevenue());
      totalCount += point.paidPaymentCount();
    }

    return new OperationsViews.RevenueReport(
        range.from(),
        range.to(),
        propertyId,
        totalRevenue,
        totalCount,
        OperationsViews.CURRENCY,
        OperationsViews.SIMULATED_REVENUE_LABEL,
        new ArrayList<>(byDate.values()));
  }

  @PreAuthorize("hasRole('ADMIN')")
  public OperationsViews.BookingsReport monitorBookings(
      LocalDate from, LocalDate to, UUID propertyId) {
    DateRange range = resolveRange(from, to);
    if (propertyId != null) {
      requirePropertyExists(propertyId);
    }
    var params =
        new MapSqlParameterSource()
            .addValue("propertyId", propertyId)
            .addValue("zone", bookingProperties.zone().getId());
    String bookingPropFilter = propertyId == null ? "" : " AND b.property_id = :propertyId";
    String bookingDateClause = buildBookingCreatedClause(range.from(), range.to(), params);

    Map<String, Long> bookingsByStatus = new LinkedHashMap<>();
    for (BookingStatus status : BookingStatus.values()) {
      bookingsByStatus.put(status.name(), 0L);
    }
    jdbc.query(
        "SELECT b.status, count(*) AS cnt FROM bookings b WHERE 1=1"
            + bookingPropFilter
            + bookingDateClause
            + " GROUP BY b.status",
        params,
        rs -> {
          bookingsByStatus.put(rs.getString("status"), rs.getLong("cnt"));
        });

    Map<LocalDate, OperationsViews.DailyBookingPoint> byDate = new LinkedHashMap<>();
    for (LocalDate d = range.from(); !d.isAfter(range.to()); d = d.plusDays(1)) {
      byDate.put(d, new OperationsViews.DailyBookingPoint(d, 0L));
    }

    jdbc.query(
        """
        SELECT (b.created_at AT TIME ZONE :zone)::date AS created_date,
               count(*) AS cnt
        FROM bookings b
        WHERE 1=1
        """
            + bookingPropFilter
            + bookingDateClause
            + " GROUP BY created_date ORDER BY created_date ASC",
        params,
        rs -> {
          LocalDate d = rs.getObject("created_date", LocalDate.class);
          byDate.put(d, new OperationsViews.DailyBookingPoint(d, rs.getLong("cnt")));
        });

    long totalBookings =
        byDate.values().stream().mapToLong(OperationsViews.DailyBookingPoint::bookingsCount).sum();

    return new OperationsViews.BookingsReport(
        range.from(),
        range.to(),
        propertyId,
        totalBookings,
        bookingsByStatus,
        new ArrayList<>(byDate.values()));
  }

  private record PropertyHeader(UUID id, String name, String slug, String status) {}

  private record DateRange(LocalDate from, LocalDate to) {}

  private DateRange resolveRange(LocalDate from, LocalDate to) {
    LocalDate today = LocalDate.ofInstant(clock.instant(), bookingProperties.zone());
    LocalDate effectiveTo = to != null ? to : today;
    LocalDate effectiveFrom = from != null ? from : effectiveTo.minusDays(6);
    if (effectiveFrom.isAfter(effectiveTo)
        || ChronoUnit.DAYS.between(effectiveFrom, effectiveTo) > 366) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date range.");
    }
    return new DateRange(effectiveFrom, effectiveTo);
  }

  private String buildBookingCreatedClause(
      LocalDate from, LocalDate to, MapSqlParameterSource params) {
    String clause = "";
    if (from != null) {
      Instant fromStart = from.atStartOfDay(bookingProperties.zone()).toInstant();
      clause += " AND b.created_at >= :fromStart";
      params.addValue("fromStart", Timestamp.from(fromStart));
    }
    if (to != null) {
      Instant toEndExclusive = to.plusDays(1).atStartOfDay(bookingProperties.zone()).toInstant();
      clause += " AND b.created_at < :toEndExclusive";
      params.addValue("toEndExclusive", Timestamp.from(toEndExclusive));
    }
    return clause;
  }

  private String buildPaymentPaidClause(
      LocalDate from, LocalDate to, MapSqlParameterSource params) {
    String clause = "";
    if (from != null) {
      Instant fromStart = from.atStartOfDay(bookingProperties.zone()).toInstant();
      clause += " AND p.paid_at >= :paidFromStart";
      params.addValue("paidFromStart", Timestamp.from(fromStart));
    }
    if (to != null) {
      Instant toEndExclusive = to.plusDays(1).atStartOfDay(bookingProperties.zone()).toInstant();
      clause += " AND p.paid_at < :paidToEndExclusive";
      params.addValue("paidToEndExclusive", Timestamp.from(toEndExclusive));
    }
    return clause;
  }

  private void requirePropertyExists(UUID propertyId) {
    Boolean exists =
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM properties WHERE id = :id)",
            Map.of("id", propertyId),
            Boolean.class);
    if (!Boolean.TRUE.equals(exists)) throw notFound();
  }

  private long count(String sql, MapSqlParameterSource params) {
    Long value = jdbc.queryForObject(sql, params, Long.class);
    return value == null ? 0L : value;
  }

  private BookingViews.Summary mapBookingSummary(ResultSet rs) throws SQLException {
    return new BookingViews.Summary(
        rs.getObject("id", UUID.class),
        rs.getString("booking_code"),
        rs.getObject("property_id", UUID.class),
        rs.getString("property_name"),
        rs.getObject("check_in_date", LocalDate.class),
        rs.getObject("check_out_date", LocalDate.class),
        BookingStatus.valueOf(rs.getString("status")),
        rs.getInt("guest_count"),
        rs.getBigDecimal("total_amount"),
        rs.getString("currency"),
        instant(rs, "expires_at"),
        instant(rs, "created_at"));
  }

  private Instant instant(ResultSet rs, String field) throws SQLException {
    var value = rs.getTimestamp(field);
    return value == null ? null : value.toInstant();
  }
}
