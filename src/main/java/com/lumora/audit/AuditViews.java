package com.lumora.audit;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AuditViews {
  private AuditViews() {}

  public record Entry(
      UUID id,
      UUID actorUserId,
      String action,
      String entityType,
      String entityId,
      String metadata,
      Instant createdAt) {}

  public record Filter(
      @Min(0) Integer page,
      @Min(1) @Max(100) Integer size,
      UUID actorUserId,
      @Size(max = 64) String action,
      @Size(max = 64) String entityType,
      @Size(max = 128) String entityId) {
    public Filter {
      page = page == null ? 0 : page;
      size = size == null ? 20 : size;
      action = trim(action);
      entityType = trim(entityType);
      entityId = trim(entityId);
    }

    private static String trim(String value) {
      if (value == null) return null;
      String trimmed = value.trim();
      return trimmed.isEmpty() ? null : trimmed;
    }
  }

  public record Page(List<Entry> items, int page, int size, long totalElements) {}
}
