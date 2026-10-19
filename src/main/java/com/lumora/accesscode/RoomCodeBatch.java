package com.lumora.accesscode;

import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomCodeBatch {
  private static final Logger log = LoggerFactory.getLogger(RoomCodeBatch.class);
  private final JdbcTemplate jdbc;
  private final RoomCodeGenerator generator;
  private final RoomCodeProperties properties;

  public record Result(LocalDate date, int eligible, int generated) {}

  @Transactional
  public Result generate(LocalDate date) {
    // Serialize workers across application instances; uniqueness also protects direct inserts.
    jdbc.execute("SELECT pg_advisory_xact_lock(82640912)");
    var rooms =
        jdbc.queryForList(
            """
        SELECT r.id FROM rooms r JOIN room_types t ON t.id=r.room_type_id
        JOIN properties p ON p.id=r.property_id
        WHERE r.status='ACTIVE' AND t.status='ACTIVE' AND p.status='ACTIVE' ORDER BY r.id
        """,
            UUID.class);
    int created = 0;
    for (UUID roomId : rooms) {
      if (Boolean.TRUE.equals(
          jdbc.queryForObject(
              "SELECT EXISTS(SELECT 1 FROM room_access_codes WHERE room_id=? AND valid_date=?)",
              Boolean.class,
              roomId,
              date))) continue;
      var previous =
          jdbc.queryForList(
              "SELECT code_value FROM room_access_codes WHERE room_id=? AND valid_date<? ORDER BY valid_date DESC LIMIT 1",
              String.class,
              roomId,
              date);
      String code;
      do {
        code = generator.generate(properties.length());
      } while (previous.contains(code));
      created +=
          jdbc.update(
              """
          INSERT INTO room_access_codes(id,room_id,code_value,valid_date) VALUES (?,?,?,?)
          ON CONFLICT(room_id,valid_date) DO NOTHING
          """,
              UUID.randomUUID(),
              roomId,
              code,
              date);
    }
    log.info("Room code batch date={} eligible={} generated={}", date, rooms.size(), created);
    return new Result(date, rooms.size(), created);
  }
}
