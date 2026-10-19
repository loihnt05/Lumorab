package com.lumora.accesscode;

import static com.lumora.property.PropertyService.notFound;

import com.lumora.audit.AuditService;
import com.lumora.authorization.PropertyAccess;
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

  public RoomCodeService(JdbcTemplate jdbc, RoomCodeProperties properties, RoomCodeBatch batch) {
    this(jdbc, properties, batch, null, null);
  }

  @Autowired
  public RoomCodeService(
      JdbcTemplate jdbc,
      RoomCodeProperties properties,
      RoomCodeBatch batch,
      PropertyAccess propertyAccess,
      AuditService audits) {
    this.jdbc = jdbc;
    this.properties = properties;
    this.batch = batch;
    this.propertyAccess = propertyAccess;
    this.audits = audits;
  }

  public record Code(
      UUID roomId, String codeValue, LocalDate validDate, Instant generatedAt, String status) {
    @Override
    public String toString() {
      return "RoomAccessCode[REDACTED]";
    }
  }

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
