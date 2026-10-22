package com.lumora.audit;

import com.lumora.identity.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import tools.jackson.databind.json.JsonMapper;

@Service
@Validated
@RequiredArgsConstructor
public class AuditService {
  private static final Pattern STANDALONE_PIN =
      Pattern.compile("(?<![0-9a-fA-F-])[0-9]{6}(?![0-9a-fA-F-])");
  private final NamedParameterJdbcTemplate jdbc;
  private final Clock clock;
  private final JsonMapper json;

  @Transactional
  public void record(
      String action, String entityType, Object entityId, Map<String, Object> metadata) {
    record(currentActorId(), action, entityType, entityId, metadata);
  }

  @Transactional
  public void record(
      UUID actorUserId,
      String action,
      String entityType,
      Object entityId,
      Map<String, Object> metadata) {
    String safeMetadata = sanitizeMetadata(metadata);
    var params =
        new MapSqlParameterSource()
            .addValue("id", UUID.randomUUID())
            .addValue("actorUserId", actorUserId)
            .addValue("action", action)
            .addValue("entityType", entityType)
            .addValue("entityId", String.valueOf(entityId))
            .addValue("metadata", safeMetadata)
            .addValue("createdAt", Timestamp.from(clock.instant()));
    jdbc.update(
        """
        INSERT INTO audit_logs(id, actor_user_id, action, entity_type, entity_id, metadata, created_at)
        VALUES (:id, :actorUserId, :action, :entityType, :entityId, :metadata, :createdAt)
        """,
        params);
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasRole('ADMIN')")
  public AuditViews.Page list(@NotNull @Valid AuditViews.Filter filter) {
    var params = new MapSqlParameterSource();
    String where = "1=1";
    if (filter.actorUserId() != null) {
      where += " AND actor_user_id = :actorUserId";
      params.addValue("actorUserId", filter.actorUserId());
    }
    if (filter.action() != null) {
      where += " AND action = :action";
      params.addValue("action", filter.action());
    }
    if (filter.entityType() != null) {
      where += " AND entity_type = :entityType";
      params.addValue("entityType", filter.entityType());
    }
    if (filter.entityId() != null) {
      where += " AND entity_id = :entityId";
      params.addValue("entityId", filter.entityId());
    }
    long total =
        jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE " + where, params, Long.class);
    params
        .addValue("limit", filter.size())
        .addValue("offset", (long) filter.page() * filter.size());
    var items =
        jdbc.query(
            "SELECT * FROM audit_logs WHERE "
                + where
                + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset",
            params,
            (rs, n) -> mapEntry(rs));
    return new AuditViews.Page(items, filter.page(), filter.size(), total);
  }

  static String redactText(String value) {
    if (value == null) return null;
    return STANDALONE_PIN.matcher(value).replaceAll("[REDACTED]");
  }

  static boolean isSensitiveKey(String key) {
    if (key == null) return false;
    String lower = key.toLowerCase(Locale.ROOT);
    return lower.contains("codevalue")
        || lower.contains("code_value")
        || lower.contains("accesscode")
        || lower.contains("access_code")
        || lower.contains("pin")
        || lower.contains("secret");
  }

  private String sanitizeMetadata(Map<String, Object> metadata) {
    if (metadata == null || metadata.isEmpty()) return null;
    Map<String, Object> sanitized = new LinkedHashMap<>();
    metadata.forEach(
        (key, value) -> {
          String safeKey = isSensitiveKey(key) ? "redacted" : key;
          if (isSensitiveKey(key)) {
            sanitized.put(safeKey, "[REDACTED]");
          } else if (value instanceof CharSequence text) {
            sanitized.put(safeKey, redactText(text.toString()));
          } else {
            sanitized.put(safeKey, value);
          }
        });
    return json.writeValueAsString(sanitized);
  }

  private UUID currentActorId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof CurrentUser user) {
      return user.id();
    }
    return null;
  }

  private AuditViews.Entry mapEntry(ResultSet rs) throws SQLException {
    return new AuditViews.Entry(
        rs.getObject("id", UUID.class),
        rs.getObject("actor_user_id", UUID.class),
        rs.getString("action"),
        rs.getString("entity_type"),
        rs.getString("entity_id"),
        rs.getString("metadata"),
        rs.getTimestamp("created_at").toInstant());
  }
}
