package com.lumora.accesscode;

import static com.lumora.property.PropertyService.notFound;

import java.time.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RoomCodeService {
  private final JdbcTemplate jdbc;
  private final RoomCodeProperties properties;
  private final RoomCodeBatch batch;

  public record Code(
      UUID roomId, String codeValue, LocalDate validDate, Instant generatedAt, String status) {
    @Override
    public String toString() {
      return "RoomAccessCode[REDACTED]";
    }
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasRole('ADMIN')")
  public Code find(UUID roomId, LocalDate date) {
    LocalDate effective = date == null ? LocalDate.now(properties.zone()) : date;
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

  @PreAuthorize("hasRole('ADMIN')")
  public RoomCodeBatch.Result generateToday() {
    try {
      return batch.generate(LocalDate.now(properties.zone()));
    } catch (RuntimeException exception) {
      // Do not propagate SQL exceptions containing bound code values to request logging.
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
    }
  }
}
