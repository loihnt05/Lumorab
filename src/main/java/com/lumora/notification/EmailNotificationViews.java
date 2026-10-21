package com.lumora.notification;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EmailNotificationViews {
  private EmailNotificationViews() {}

  public record Entry(
      UUID id,
      String idempotencyKey,
      UUID bookingId,
      String recipientEmail,
      String template,
      String subject,
      String payload,
      String status,
      Instant createdAt,
      Instant sentAt) {}

  public record Filter(
      @Min(0) Integer page,
      @Min(1) @Max(100) Integer size,
      UUID bookingId,
      @Size(max = 64) String template) {
    public Filter {
      page = page == null ? 0 : page;
      size = size == null ? 20 : size;
      template = trim(template);
    }

    private static String trim(String value) {
      if (value == null) return null;
      String trimmed = value.trim();
      return trimmed.isEmpty() ? null : trimmed;
    }
  }

  public record Page(List<Entry> items, int page, int size, long totalElements) {}
}
