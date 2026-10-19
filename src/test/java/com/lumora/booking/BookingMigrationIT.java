package com.lumora.booking;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@SpringBootTest(
    properties = {
      "app.clerk.issuer=",
      "app.room-codes.enabled=false",
      "app.booking.expiry-enabled=false"
    })
class BookingMigrationIT {
  @Autowired DataSource dataSource;
  @Autowired JdbcTemplate jdbc;

  @Test
  void upgradesExistingV5RowsWithoutLosingReferencesAndRerunsSafely() throws Exception {
    String schema = "stage4_migration_" + UUID.randomUUID().toString().replace("-", "");
    try {
      Flyway.configure()
          .dataSource(dataSource)
          .schemas(schema)
          .defaultSchema(schema)
          .target("5")
          .load()
          .migrate();
      UUID p = UUID.randomUUID(),
          t = UUID.randomUUID(),
          r = UUID.randomUUID(),
          u = UUID.randomUUID(),
          b = UUID.randomUUID();
      try (var connection = dataSource.getConnection()) {
        connection.setSchema(schema);
        try {
          var seed = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
          seed.update("INSERT INTO properties(id,name,slug) VALUES (?,'Legacy','legacy')", p);
          seed.update("INSERT INTO users(id,clerk_user_id) VALUES (?,'legacy-user')", u);
          seed.update(
              "INSERT INTO room_types(id,property_id,name,base_price,max_guests,bed_count) VALUES (?,?,'Legacy',600000,2,1)",
              t,
              p);
          seed.update(
              "INSERT INTO rooms(id,property_id,room_type_id,room_number,name) VALUES (?,?,?,'No5','Legacy Room')",
              r,
              p,
              t);
          seed.update(
              "INSERT INTO bookings(id,user_id,property_id,check_in_date,check_out_date,status,expires_at) VALUES (?,?,?,'2027-01-10','2027-01-13','PENDING_PAYMENT',now()+interval '15 minutes')",
              b,
              u,
              p);
          seed.update(
              "INSERT INTO booking_rooms(id,booking_id,room_id,property_id) VALUES (?,?,?,?)",
              UUID.randomUUID(),
              b,
              r,
              p);
        } finally {
          connection.setSchema("public");
        }
      }
      var flyway =
          Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load();
      assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT booking_code FROM " + schema + ".bookings WHERE id=?", String.class, b))
          .isEqualTo("LEGACY-" + b);
      assertThat(
              jdbc.queryForObject(
                  "SELECT total_amount FROM " + schema + ".bookings WHERE id=?",
                  java.math.BigDecimal.class,
                  b))
          .isEqualByComparingTo("1800000");
      assertThat(
              jdbc.queryForObject(
                  "SELECT room_type_id FROM " + schema + ".booking_rooms WHERE booking_id=?",
                  UUID.class,
                  b))
          .isEqualTo(t);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM " + schema + ".booking_guests", Integer.class))
          .isZero();
      flyway.validate();
      assertThat(flyway.migrate().migrationsExecuted).isZero();
    } finally {
      // Only this test's generated schema; never the application schema or user data.
      jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }
  }
}
