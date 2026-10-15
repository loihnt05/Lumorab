package com.lumora.health;

import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class HealthController {
  private final JdbcTemplate jdbc;

  public HealthController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping("/api/health")
  @Operation(summary = "Check API and PostgreSQL connectivity")
  public HealthResponse health() {
    try {
      jdbc.queryForObject("SELECT 1", Integer.class);
    } catch (DataAccessException exception) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Database unavailable", exception);
    }
    return new HealthResponse("UP", "lumorab", "UP", Instant.now());
  }

  public record HealthResponse(String status, String service, String database, Instant timestamp) {}
}
