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

  @GetMapping("/api/health/liveness")
  @Operation(summary = "Check JVM process liveness")
  public HealthResponse liveness() {
    return new HealthResponse("UP", "lumorab", "SKIPPED", Instant.now());
  }

  @GetMapping("/api/health/readiness")
  @Operation(summary = "Check database connectivity and Flyway schema readiness")
  public ReadinessResponse readiness() {
    try {
      jdbc.queryForObject("SELECT 1", Integer.class);
      Integer applied =
          jdbc.queryForObject(
              "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);
      Integer failed =
          jdbc.queryForObject(
              "SELECT count(*) FROM flyway_schema_history WHERE success = false", Integer.class);
      if (failed != null && failed > 0) {
        throw new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE, "Failed database migration detected");
      }
      return new ReadinessResponse(
          "READY", "lumorab", "UP", applied == null ? 0 : applied, Instant.now());
    } catch (DataAccessException exception) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Database unavailable", exception);
    }
  }

  public record HealthResponse(String status, String service, String database, Instant timestamp) {}

  public record ReadinessResponse(
      String status, String service, String database, int appliedMigrations, Instant timestamp) {}
}
