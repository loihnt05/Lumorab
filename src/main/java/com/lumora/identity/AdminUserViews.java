package com.lumora.identity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AdminUserViews {
  private AdminUserViews() {}

  public record Filter(
      @Min(0) Integer page,
      @Min(1) @Max(100) Integer size,
      Role role,
      UserStatus status,
      @Size(max = 200) String q) {
    public Filter {
      page = page == null ? 0 : page;
      size = size == null ? 20 : size;
      q = q == null || q.trim().isEmpty() ? null : q.trim();
    }
  }

  public record RoleRequest(@NotNull Role role) {}

  public record StatusRequest(@NotNull UserStatus status) {}

  public record Page(List<UserProfile> items, int page, int size, long totalElements) {}
}
