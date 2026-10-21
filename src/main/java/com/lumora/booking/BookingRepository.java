package com.lumora.booking;

import static com.lumora.property.PropertyService.notFound;

import com.lumora.pricing.PriceQuote;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
@RequiredArgsConstructor
public class BookingRepository {
  public static final String CHECK_IN_QR_PREFIX = "LMR-CHK-";

  private final NamedParameterJdbcTemplate jdbc;

  public record LockedRoom(
      UUID id,
      UUID propertyId,
      UUID typeId,
      String number,
      String name,
      String status,
      boolean blocked) {}

  public record Rate(BigDecimal price, int capacity, String typeStatus, String propertyStatus) {}

  public record State(
      UUID id,
      UUID propertyId,
      LocalDate checkIn,
      LocalDate checkOut,
      BookingStatus status,
      Instant expiresAt) {}

  public record QrRow(
      UUID bookingId,
      UUID userId,
      UUID propertyId,
      String propertyName,
      String bookingCode,
      BookingStatus status,
      LocalDate checkIn,
      LocalDate checkOut,
      String qrToken,
      String primaryGuestName) {}

  public static String checkInQrTokenFor(UUID bookingId) {
    return CHECK_IN_QR_PREFIX + bookingId.toString().replace("-", "").toUpperCase(Locale.ROOT);
  }

  public LockedRoom lockRoom(UUID roomId) {
    return jdbc
        .query(
            "SELECT * FROM rooms WHERE id=:id FOR UPDATE",
            Map.of("id", roomId),
            (rs, n) ->
                new LockedRoom(
                    roomId,
                    rs.getObject("property_id", UUID.class),
                    rs.getObject("room_type_id", UUID.class),
                    rs.getString("room_number"),
                    rs.getString("name"),
                    rs.getString("status"),
                    rs.getBoolean("is_blocked")))
        .stream()
        .findFirst()
        .orElseThrow(() -> notFound());
  }

  public Rate lockRate(LockedRoom room) {
    return jdbc
        .query(
            """
        SELECT t.base_price,t.max_guests,t.status,p.status FROM room_types t
        JOIN properties p ON p.id=t.property_id
        WHERE t.id=:typeId AND p.id=:propertyId FOR SHARE OF t,p
        """,
            Map.of("typeId", room.typeId(), "propertyId", room.propertyId()),
            (rs, n) ->
                new Rate(rs.getBigDecimal(1), rs.getInt(2), rs.getString(3), rs.getString(4)))
        .stream()
        .findFirst()
        .orElseThrow(() -> notFound());
  }

  public boolean hasConflict(UUID roomId, LocalDate in, LocalDate out, Instant now, UUID exclude) {
    String excluded = exclude == null ? "" : " AND b.id<>:exclude";
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
        SELECT EXISTS(SELECT 1 FROM booking_rooms br JOIN bookings b ON b.id=br.booking_id
        WHERE br.room_id=:roomId AND
        """
                + BookingInventoryPolicy.OVERLAPPING_OCCUPANCY
                + excluded
                + ")",
            BookingInventoryPolicy.parameters(in, out, now)
                .addValue("roomId", roomId)
                .addValue("exclude", exclude),
            Boolean.class));
  }

  public void insert(
      UUID id,
      UUID userId,
      CreateBookingRequest request,
      LockedRoom room,
      PriceQuote quote,
      Instant expiresAt) {
    var p =
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("userId", userId)
            .addValue("code", "LM-" + id.toString().replace("-", "").toUpperCase(Locale.ROOT))
            .addValue("propertyId", request.propertyId())
            .addValue("checkIn", request.checkIn())
            .addValue("checkOut", request.checkOut())
            .addValue("guests", request.guests())
            .addValue("amount", quote.totalAmount())
            .addValue("currency", quote.currency())
            .addValue("expiresAt", Timestamp.from(expiresAt))
            .addValue("specialRequest", request.specialRequest());
    jdbc.update(
        """
        INSERT INTO bookings(id,booking_code,user_id,property_id,check_in_date,check_out_date,
          status,guest_count,subtotal,discount_amount,total_amount,currency,expires_at,special_request)
        VALUES (:id,:code,:userId,:propertyId,:checkIn,:checkOut,'PENDING_PAYMENT',:guests,
          :amount,0,:amount,:currency,:expiresAt,:specialRequest)
        """,
        p);
    p.addValue("lineId", UUID.randomUUID())
        .addValue("roomId", room.id())
        .addValue("typeId", room.typeId())
        .addValue("number", room.number())
        .addValue("name", room.name())
        .addValue("price", quote.pricePerNight())
        .addValue("nights", quote.numberOfNights());
    jdbc.update(
        """
        INSERT INTO booking_rooms(id,booking_id,room_id,property_id,room_type_id,room_number,
          room_name,price_per_night,number_of_nights,room_total)
        VALUES (:lineId,:id,:roomId,:propertyId,:typeId,:number,:name,:price,:nights,:amount)
        """,
        p);
    p.addValue("guestId", UUID.randomUUID())
        .addValue("fullName", request.primaryGuest().fullName().trim())
        .addValue("phone", request.primaryGuest().phone().trim())
        .addValue("email", request.primaryGuest().email().trim());
    jdbc.update(
        """
        INSERT INTO booking_guests(id,booking_id,full_name,phone,email,is_primary_guest)
        VALUES (:guestId,:id,:fullName,:phone,:email,true)
        """,
        p);
  }

  public BookingViews.Page list(UUID userId, UUID propertyId, BookingFilter f) {
    var p = new MapSqlParameterSource();
    String where = scope(userId, propertyId, p);
    if (f.date() != null) {
      where += " AND b.check_in_date<=:date AND b.check_out_date>:date";
      p.addValue("date", f.date());
    }
    if (f.status() != null) {
      where += " AND b.status=:status";
      p.addValue("status", f.status().name());
    }
    if (f.bookingCode() != null) {
      where += " AND b.booking_code=:code";
      p.addValue("code", f.bookingCode());
    }
    if (f.roomId() != null) {
      where +=
          " AND EXISTS(SELECT 1 FROM booking_rooms br WHERE br.booking_id=b.id AND br.room_id=:roomId)";
      p.addValue("roomId", f.roomId());
    }
    long total =
        jdbc.queryForObject("SELECT count(*) FROM bookings b WHERE " + where, p, Long.class);
    p.addValue("limit", f.size()).addValue("offset", (long) f.page() * f.size());
    var items =
        jdbc.query(
            "SELECT b.*,p.name AS property_name FROM bookings b JOIN properties p ON p.id=b.property_id WHERE "
                + where
                + " ORDER BY b.created_at DESC,b.id DESC LIMIT :limit OFFSET :offset",
            p,
            (rs, n) -> summary(rs));
    return new BookingViews.Page(items, f.page(), f.size(), total);
  }

  public BookingViews.Detail detail(UUID id, UUID userId, UUID propertyId) {
    var p = new MapSqlParameterSource("id", id);
    var result =
        jdbc
            .query(
                "SELECT b.*,p.name AS property_name FROM bookings b JOIN properties p ON p.id=b.property_id WHERE b.id=:id AND "
                    + scope(userId, propertyId, p),
                p,
                (rs, n) ->
                    new BookingViews.Detail(
                        summary(rs),
                        rs.getBigDecimal("subtotal"),
                        rs.getBigDecimal("discount_amount"),
                        rs.getString("special_request"),
                        List.of(),
                        List.of()))
            .stream()
            .findFirst()
            .orElseThrow(() -> notFound());
    var rooms =
        jdbc.query(
            "SELECT * FROM booking_rooms WHERE booking_id=:id ORDER BY room_number,room_id",
            p,
            (rs, n) ->
                new BookingViews.RoomLine(
                    rs.getObject("room_id", UUID.class),
                    rs.getObject("room_type_id", UUID.class),
                    rs.getString("room_number"),
                    rs.getString("room_name"),
                    rs.getBigDecimal("price_per_night"),
                    rs.getInt("number_of_nights"),
                    rs.getBigDecimal("room_total")));
    var guests =
        jdbc.query(
            "SELECT * FROM booking_guests WHERE booking_id=:id ORDER BY is_primary_guest DESC,id",
            p,
            (rs, n) ->
                new BookingViews.Guest(
                    rs.getString("full_name"),
                    rs.getString("phone"),
                    rs.getString("email"),
                    rs.getBoolean("is_primary_guest")));
    return new BookingViews.Detail(
        result.booking(),
        result.subtotal(),
        result.discountAmount(),
        result.specialRequest(),
        rooms,
        guests);
  }

  private String scope(UUID userId, UUID propertyId, MapSqlParameterSource p) {
    String where = "1=1";
    if (userId != null) {
      where += " AND b.user_id=:userId";
      p.addValue("userId", userId);
    }
    if (propertyId != null) {
      where += " AND b.property_id=:propertyId";
      p.addValue("propertyId", propertyId);
    }
    return where;
  }

  private BookingViews.Summary summary(ResultSet rs) throws SQLException {
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

  public List<UUID> roomIds(UUID bookingId) {
    return jdbc.queryForList(
        "SELECT room_id FROM booking_rooms WHERE booking_id=:id ORDER BY room_id",
        Map.of("id", bookingId),
        UUID.class);
  }

  public State lockBooking(UUID id) {
    return jdbc
        .query(
            "SELECT * FROM bookings WHERE id=:id FOR UPDATE",
            Map.of("id", id),
            (rs, n) ->
                new State(
                    id,
                    rs.getObject("property_id", UUID.class),
                    rs.getObject("check_in_date", LocalDate.class),
                    rs.getObject("check_out_date", LocalDate.class),
                    BookingStatus.valueOf(rs.getString("status")),
                    instant(rs, "expires_at")))
        .stream()
        .findFirst()
        .orElseThrow(() -> notFound());
  }

  public UUID findPropertyId(UUID id) {
    return jdbc
        .queryForList("SELECT property_id FROM bookings WHERE id=:id", Map.of("id", id), UUID.class)
        .stream()
        .findFirst()
        .orElseThrow(() -> notFound());
  }

  public void changeStatus(State booking, BookingStatus next) {
    booking.status().requireTransitionTo(next);
    if (next == BookingStatus.CONFIRMED) {
      if (jdbc.update(
              """
              UPDATE bookings
              SET status=:next,
                  check_in_qr_token = COALESCE(check_in_qr_token, :qrToken)
              WHERE id=:id AND status=:previous
              """,
              Map.of(
                  "next",
                  next.name(),
                  "id",
                  booking.id(),
                  "previous",
                  booking.status().name(),
                  "qrToken",
                  checkInQrTokenFor(booking.id())))
          != 1) throw conflict();
      return;
    }
    if (jdbc.update(
            "UPDATE bookings SET status=:next WHERE id=:id AND status=:previous",
            Map.of("next", next.name(), "id", booking.id(), "previous", booking.status().name()))
        != 1) throw conflict();
  }

  public void markCancelled(State booking, UUID cancelledByUserId, String reason, Instant now) {
    booking.status().requireTransitionTo(BookingStatus.CANCELLED);
    if (jdbc.update(
            """
            UPDATE bookings
            SET status='CANCELLED',
                cancelled_at=:now,
                cancelled_by_user_id=:cancelledBy,
                cancellation_reason=:reason
            WHERE id=:id AND status=:previous
            """,
            new MapSqlParameterSource("id", booking.id())
                .addValue("previous", booking.status().name())
                .addValue("now", Timestamp.from(now))
                .addValue("cancelledBy", cancelledByUserId)
                .addValue("reason", reason))
        != 1) throw conflict();
  }

  public QrRow findQrRow(UUID id) {
    return jdbc
        .query(
            """
            SELECT b.id, b.user_id, b.property_id, p.name AS property_name,
                   b.booking_code, b.status, b.check_in_date, b.check_out_date,
                   b.check_in_qr_token,
                   (SELECT g.full_name FROM booking_guests g
                    WHERE g.booking_id = b.id ORDER BY g.is_primary_guest DESC, g.id LIMIT 1) AS primary_guest_name
            FROM bookings b JOIN properties p ON p.id = b.property_id
            WHERE b.id = :id
            """,
            Map.of("id", id),
            (rs, n) -> mapQrRow(rs))
        .stream()
        .findFirst()
        .orElseThrow(() -> notFound());
  }

  public QrRow findQrRowByToken(String qrToken) {
    return jdbc
        .query(
            """
            SELECT b.id, b.user_id, b.property_id, p.name AS property_name,
                   b.booking_code, b.status, b.check_in_date, b.check_out_date,
                   b.check_in_qr_token,
                   (SELECT g.full_name FROM booking_guests g
                    WHERE g.booking_id = b.id ORDER BY g.is_primary_guest DESC, g.id LIMIT 1) AS primary_guest_name
            FROM bookings b JOIN properties p ON p.id = b.property_id
            WHERE b.check_in_qr_token = :qrToken
            """,
            Map.of("qrToken", qrToken),
            (rs, n) -> mapQrRow(rs))
        .stream()
        .findFirst()
        .orElseThrow(() -> notFound());
  }

  private QrRow mapQrRow(ResultSet rs) throws SQLException {
    return new QrRow(
        rs.getObject("id", UUID.class),
        rs.getObject("user_id", UUID.class),
        rs.getObject("property_id", UUID.class),
        rs.getString("property_name"),
        rs.getString("booking_code"),
        BookingStatus.valueOf(rs.getString("status")),
        rs.getObject("check_in_date", LocalDate.class),
        rs.getObject("check_out_date", LocalDate.class),
        rs.getString("check_in_qr_token"),
        rs.getString("primary_guest_name"));
  }

  public void markCheckedIn(State booking, Instant now) {
    booking.status().requireTransitionTo(BookingStatus.CHECKED_IN);
    if (jdbc.update(
            "UPDATE bookings SET status='CHECKED_IN',checked_in_at=:now WHERE id=:id AND status=:previous",
            new MapSqlParameterSource("id", booking.id())
                .addValue("previous", booking.status().name())
                .addValue("now", Timestamp.from(now)))
        != 1) throw conflict();
  }

  public void markCheckedOut(State booking, Instant now) {
    booking.status().requireTransitionTo(BookingStatus.CHECKED_OUT);
    if (jdbc.update(
            "UPDATE bookings SET status='CHECKED_OUT',checked_out_at=:now WHERE id=:id AND status=:previous",
            new MapSqlParameterSource("id", booking.id())
                .addValue("previous", booking.status().name())
                .addValue("now", Timestamp.from(now)))
        != 1) throw conflict();
  }

  public void markCompleted(State booking, Instant now) {
    booking.status().requireTransitionTo(BookingStatus.COMPLETED);
    if (jdbc.update(
            "UPDATE bookings SET status='COMPLETED',completed_at=:now WHERE id=:id AND status=:previous",
            new MapSqlParameterSource("id", booking.id())
                .addValue("previous", booking.status().name())
                .addValue("now", Timestamp.from(now)))
        != 1) throw conflict();
  }

  public List<UUID> expiredCandidates(Instant now, int limit) {
    return jdbc.queryForList(
        """
        SELECT id FROM bookings WHERE status='PENDING_PAYMENT' AND expires_at<=:now
        ORDER BY expires_at,id LIMIT :limit
        """,
        new MapSqlParameterSource("now", Timestamp.from(now)).addValue("limit", limit),
        UUID.class);
  }

  public int expire(UUID id, Instant now) {
    BookingStatus.PENDING_PAYMENT.requireTransitionTo(BookingStatus.EXPIRED);
    return jdbc.update(
        """
        UPDATE bookings SET status='EXPIRED' WHERE id=:id AND status='PENDING_PAYMENT' AND expires_at<=:now
        """,
        new MapSqlParameterSource("id", id).addValue("now", Timestamp.from(now)));
  }

  public static ResponseStatusException conflict() {
    return new ResponseStatusException(
        HttpStatus.CONFLICT, "The room or booking is no longer available.");
  }
}
