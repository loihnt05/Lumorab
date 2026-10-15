package com.lumora;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

// Explicit opt-in: ./mvnw -Pintegration verify, with the local PostgreSQL running.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FoundationIT {
  @LocalServerPort int port;
  @Autowired Flyway flyway;
  @Autowired JdbcTemplate jdbc;

  @Test
  void migrationsValidateAndRerunWithoutChanges() {
    flyway.validate();
    assertThat(flyway.migrate().migrationsExecuted).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM pg_proc WHERE proname = 'set_updated_at'", Integer.class))
        .isPositive();
  }

  @Test
  void healthAndOpenApiAreAvailable() throws Exception {
    try (HttpClient client = HttpClient.newHttpClient()) {
      var health =
          client.send(
              HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/health"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(health.statusCode()).isEqualTo(200);
      assertThat(health.body()).contains("\"database\":\"UP\"");
      var docs =
          client.send(
              HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(docs.statusCode()).isEqualTo(200);
      assertThat(docs.body()).contains("/api/health");
    }
  }
}
