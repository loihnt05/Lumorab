package com.lumora.identity;

import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserIdentityWriter {
  private final NamedParameterJdbcTemplate jdbc;

  public UserIdentityWriter(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // Atomic PostgreSQL upsert handles simultaneous first requests and out-of-order profile updates.
  // Only profile columns are updated on conflict, never role, status, local ID or contact phone.
  public void upsert(IdentitySnapshot identity) {
    var params =
        new MapSqlParameterSource()
            .addValue("id", UUID.randomUUID())
            .addValue("subject", identity.clerkUserId())
            .addValue("email", identity.email().value())
            .addValue("firstName", identity.firstName().value())
            .addValue("lastName", identity.lastName().value())
            .addValue("hasEmail", identity.email().supplied())
            .addValue("hasFirstName", identity.firstName().supplied())
            .addValue("hasLastName", identity.lastName().supplied())
            .addValue("issuedAt", Timestamp.from(identity.issuedAt()));
    jdbc.update(
        """
        INSERT INTO users (id, clerk_user_id, email, first_name, last_name, role, status, profile_synced_at)
        VALUES (:id, :subject, :email, :firstName, :lastName, 'USER', 'ACTIVE', :issuedAt)
        ON CONFLICT (clerk_user_id) DO UPDATE SET
          email = CASE WHEN :hasEmail THEN EXCLUDED.email ELSE users.email END,
          first_name = CASE WHEN :hasFirstName THEN EXCLUDED.first_name ELSE users.first_name END,
          last_name = CASE WHEN :hasLastName THEN EXCLUDED.last_name ELSE users.last_name END,
          profile_synced_at = EXCLUDED.profile_synced_at
        WHERE users.profile_synced_at IS NULL OR EXCLUDED.profile_synced_at > users.profile_synced_at
        """,
        params);
  }
}
