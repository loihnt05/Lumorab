package com.lumora.accesscode;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class RoomCodeServiceTest {
  @Test
  void generatorPreservesConfiguredStringLength() {
    var generator = new RoomCodeGenerator();
    for (int length : new int[] {4, 6, 12})
      for (int i = 0; i < 50; i++)
        assertThat(generator.generate(length)).matches("[0-9]{" + length + "}");
    assertThatThrownBy(() -> generator.generate(0)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void manualGenerationUsesConfiguredZoneAndSanitizesSecretBearingErrors() {
    var batch = mock(RoomCodeBatch.class);
    var zone = ZoneId.of("Pacific/Kiritimati");
    var today = LocalDate.now(zone);
    when(batch.generate(today))
        .thenThrow(new DataIntegrityViolationException("secret-code-in-sql"));
    var service =
        new RoomCodeService(mock(JdbcTemplate.class), new RoomCodeProperties(zone, 6), batch);
    assertThatThrownBy(service::generateToday)
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> {
              assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
              assertThat(e.getCause()).isNull();
              assertThat(e.toString()).doesNotContain("secret-code-in-sql");
            });
    verify(batch).generate(today);
  }

  @Test
  void codeDtoNeverPrintsSecretInToString() {
    var code =
        new RoomCodeService.Code(
            UUID.randomUUID(), "001234", LocalDate.now(), Instant.now(), "ACTIVE");
    assertThat(code.toString()).doesNotContain("001234");
  }
}
