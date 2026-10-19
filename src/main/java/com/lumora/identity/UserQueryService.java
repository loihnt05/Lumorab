package com.lumora.identity;

import com.lumora.audit.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

@Service
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class UserQueryService {
  private final UserRepository users;
  private final NamedParameterJdbcTemplate jdbc;
  private final AuditService audits;

  public UserQueryService(UserRepository users) {
    this(users, null, null);
  }

  @Autowired
  public UserQueryService(
      UserRepository users, NamedParameterJdbcTemplate jdbc, AuditService audits) {
    this.users = users;
    this.jdbc = jdbc;
    this.audits = audits;
  }

  @Transactional(readOnly = true)
  public UserProfile find(UUID id) {
    return users
        .findById(id)
        .map(UserProfile::from)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  @Transactional(readOnly = true)
  public AdminUserViews.Page list(@NotNull @Valid AdminUserViews.Filter filter) {
    var params = new MapSqlParameterSource();
    String where = "1=1";
    if (filter.role() != null) {
      where += " AND u.role = :role";
      params.addValue("role", filter.role().name());
    }
    if (filter.status() != null) {
      where += " AND u.status = :status";
      params.addValue("status", filter.status().name());
    }
    if (filter.q() != null) {
      where +=
          """
           AND (
             LOWER(COALESCE(u.email, '')) LIKE :q
             OR LOWER(COALESCE(u.first_name, '')) LIKE :q
             OR LOWER(COALESCE(u.last_name, '')) LIKE :q
             OR LOWER(u.clerk_user_id) LIKE :q
           )
          """;
      params.addValue("q", "%" + filter.q().toLowerCase(Locale.ROOT) + "%");
    }
    long total =
        jdbc.queryForObject("SELECT count(*) FROM users u WHERE " + where, params, Long.class);
    params
        .addValue("limit", filter.size())
        .addValue("offset", (long) filter.page() * filter.size());
    var items =
        jdbc.query(
            "SELECT u.* FROM users u WHERE "
                + where
                + " ORDER BY u.created_at DESC, u.id DESC LIMIT :limit OFFSET :offset",
            params,
            (rs, n) -> mapProfile(rs));
    return new AdminUserViews.Page(items, filter.page(), filter.size(), total);
  }

  @Transactional
  public UserProfile changeRole(UUID id, @NotNull Role nextRole) {
    var current = lockUser(id);
    if (current.role() == Role.ADMIN
        && current.status() == UserStatus.ACTIVE
        && nextRole != Role.ADMIN) {
      requireAnotherActiveAdmin(id);
    }
    if (current.role() != nextRole) {
      jdbc.update(
          "UPDATE users SET role = :role WHERE id = :id",
          Map.of("role", nextRole.name(), "id", id));
      if (audits != null) {
        audits.record(
            "USER_ROLE_CHANGE",
            "USER",
            id,
            Map.of("previousRole", current.role().name(), "newRole", nextRole.name()));
      }
    }
    return queryById(id);
  }

  @Transactional
  public UserProfile changeStatus(UUID id, @NotNull UserStatus nextStatus) {
    var current = lockUser(id);
    if (current.role() == Role.ADMIN
        && current.status() == UserStatus.ACTIVE
        && nextStatus == UserStatus.DISABLED) {
      requireAnotherActiveAdmin(id);
    }
    if (current.status() != nextStatus) {
      jdbc.update(
          "UPDATE users SET status = :status WHERE id = :id",
          Map.of("status", nextStatus.name(), "id", id));
      if (audits != null) {
        audits.record(
            "USER_STATUS_CHANGE",
            "USER",
            id,
            Map.of("previousStatus", current.status().name(), "newStatus", nextStatus.name()));
      }
    }
    return queryById(id);
  }

  private UserProfile lockUser(UUID id) {
    return jdbc
        .query(
            "SELECT * FROM users WHERE id = :id FOR UPDATE",
            Map.of("id", id),
            (rs, n) -> mapProfile(rs))
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  private UserProfile queryById(UUID id) {
    return jdbc
        .query("SELECT * FROM users WHERE id = :id", Map.of("id", id), (rs, n) -> mapProfile(rs))
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  private void requireAnotherActiveAdmin(UUID targetAdminId) {
    // Lock all active admin rows in deterministic order to prevent concurrent last-admin demotion.
    List<UUID> activeAdmins =
        jdbc.queryForList(
            "SELECT id FROM users WHERE role = 'ADMIN' AND status = 'ACTIVE' ORDER BY id FOR UPDATE",
            Map.of(),
            UUID.class);
    long others = activeAdmins.stream().filter(adminId -> !adminId.equals(targetAdminId)).count();
    if (others == 0) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Cannot demote or disable the last active admin.");
    }
  }

  private UserProfile mapProfile(ResultSet rs) throws SQLException {
    return new UserProfile(
        rs.getObject("id", UUID.class),
        rs.getString("clerk_user_id"),
        rs.getString("email"),
        rs.getString("first_name"),
        rs.getString("last_name"),
        rs.getString("phone"),
        Role.valueOf(rs.getString("role")),
        UserStatus.valueOf(rs.getString("status")),
        instant(rs, "created_at"),
        instant(rs, "updated_at"));
  }

  private Instant instant(ResultSet rs, String field) throws SQLException {
    var ts = rs.getTimestamp(field);
    return ts == null ? null : ts.toInstant();
  }
}
