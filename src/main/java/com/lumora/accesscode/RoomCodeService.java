package com.lumora.accesscode;

import static com.lumora.property.PropertyService.notFound;

import com.lumora.audit.AuditService;
import com.lumora.authorization.PropertyAccess;
import com.lumora.booking.BookingRepository;
import com.lumora.booking.BookingStatus;
import com.lumora.identity.security.CurrentUser;
import java.time.*;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RoomCodeService {
  private final JdbcTemplate jdbc;
  private final RoomCodeProperties properties;
  private final RoomCodeBatch batch;
  private final PropertyAccess propertyAccess;
  private final AuditService audits;
  private final Clock clock;

  public RoomCodeService(JdbcTemplate jdbc, RoomCodeProperties properties, RoomCodeBatch batch) {
    this(jdbc, properties, batch, null, null, Clock.systemUTC());
  }

  @Autowired
  public RoomCodeService(
      JdbcTemplate jdbc,
      RoomCodeProperties properties,
      RoomCodeBatch batch,
      PropertyAccess propertyAccess,
      AuditService audits,
      Clock clock) {
    this.jdbc = jdbc;
    this.properties = properties;
    this.batch = batch;
    this.propertyAccess = propertyAccess;
    this.audits = audits;
    this.clock = clock == null ? Clock.systemUTC() : clock;
  }

  public record Code(
      UUID roomId, String codeValue, LocalDate validDate, Instant generatedAt, String status) {
    @Override
    public String toString() {
      return "RoomAccessCode[REDACTED]";
    }
  }

  private record GuestBookingRow(
      UUID bookingId,
      UUID userId,
      UUID roomId,
      BookingStatus status,
      LocalDate checkIn,
      LocalDate checkOut) {}

  @Transactional
  @PreAuthorize("hasRole('ADMIN')")
  public Code find(UUID roomId, LocalDate date) {
    LocalDate effective = date == null ? LocalDate.now(properties.zone()) : date;
    Code code = queryActiveCode(roomId, effective);
    if (audits != null) {
      audits.record(
          "ROOM_CODE_READ",
          "ROOM",
          roomId,
          Map.of("validDate", effective.toString(), "scope", "ADMIN"));
    }
    return code;
  }

  @Transactional
  @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
  public Code findCurrentForManager(Authentication authentication, UUID propertyId, UUID roomId) {
    if (propertyAccess == null || !propertyAccess.canManage(authentication, propertyId)) {
      throw new AccessDeniedException("Forbidden");
    }
    Boolean belongs =
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM rooms WHERE id=? AND property_id=?)",
            Boolean.class,
            roomId,
            propertyId);
    if (!Boolean.TRUE.equals(belongs)) throw notFound();
    LocalDate today = LocalDate.now(properties.zone());
    Code code = queryActiveCode(roomId, today);
    if (audits != null) {
      audits.record(
          "ROOM_CODE_READ",
          "ROOM",
          roomId,
          Map.of(
              "propertyId",
              propertyId.toString(),
              "validDate",
              today.toString(),
              "scope",
              "MANAGER"));
    }
    return code;
  }

  @Transactional
  @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
  public Code findCurrentForManagerByRoom(Authentication authentication, UUID roomId) {
    UUID propertyId =
        jdbc
            .query(
                "SELECT property_id FROM rooms WHERE id=?",
                (rs, n) -> rs.getObject(1, UUID.class),
                roomId)
            .stream()
            .findFirst()
            .orElseThrow(() -> notFound());
    return findCurrentForManager(authentication, propertyId, roomId);
  }

  @Transactional
  @PreAuthorize("isAuthenticated()")
  public Code findCurrentForBookingGuest(CurrentUser user, UUID bookingId) {
    var row =
        jdbc
            .query(
                """
                SELECT b.id, b.user_id, br.room_id, b.status, b.check_in_date, b.check_out_date
                FROM bookings b
                JOIN booking_rooms br ON br.booking_id = b.id
                WHERE b.id = ?
                ORDER BY br.room_number, br.room_id
                LIMIT 1
                """,
                (rs, n) ->
                    new GuestBookingRow(
                        rs.getObject(1, UUID.class),
                        rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class),
                        BookingStatus.valueOf(rs.getString(4)),
                        rs.getObject(5, LocalDate.class),
                        rs.getObject(6, LocalDate.class)),
                bookingId)
            .stream()
            .findFirst()
            .orElseThrow(() -> notFound());
    if (!row.userId().equals(user.id())) {
      throw notFound();
    }
    if (row.status() != BookingStatus.CONFIRMED && row.status() != BookingStatus.CHECKED_IN) {
      throw BookingRepository.conflict();
    }
    LocalDate today = LocalDate.ofInstant(clock.instant(), properties.zone());
    if (today.isBefore(row.checkIn()) || !today.isBefore(row.checkOut())) {
      throw BookingRepository.conflict();
    }
    Code code = queryActiveCode(row.roomId(), today);
    if (audits != null) {
      audits.record(
          "ROOM_CODE_READ",
          "ROOM",
          row.roomId(),
          Map.of(
              "bookingId", bookingId.toString(), "validDate", today.toString(), "scope", "GUEST"));
    }
    return code;
  }

  @PreAuthorize("hasRole('ADMIN')")
  public RoomCodeBatch.Result generateToday() {
    try {
      var result = batch.generate(LocalDate.now(properties.zone()));
      if (audits != null && result != null) {
        audits.record(
            "ROOM_CODE_BATCH_GENERATE",
            "ROOM_ACCESS_CODE",
            result.date(),
            Map.of(
                "date",
                result.date().toString(),
                "eligible",
                result.eligible(),
                "generated",
                result.generated()));
      }
      return result;
    } catch (RuntimeException exception) {
      // Do not propagate SQL exceptions containing bound code values to request logging.
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
    }
  }

  private Code queryActiveCode(UUID roomId, LocalDate effective) {
    return jdbc
        .query(
            """
        SELECT room_id,code_value,valid_date,generated_at,status FROM room_access_codes
        WHERE room_id=? AND valid_date=? AND status='ACTIVE'
        """,
            (rs, n) ->
                new Code(
                    rs.getObject(1, UUID.class),
                    rs.getString(2),
                    rs.getObject(3, LocalDate.class),
                    rs.getTimestamp(4).toInstant(),
                    rs.getString(5)),
            roomId,
            effective)
        .stream()
        .findFirst()
        .orElseThrow(() -> notFound());
  }
}
