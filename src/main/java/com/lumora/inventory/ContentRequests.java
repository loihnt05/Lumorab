package com.lumora.inventory;

import jakarta.validation.constraints.*;
import java.util.Set;
import java.util.UUID;

public final class ContentRequests {
  private ContentRequests() {}

  public record ImageRequest(
      @NotBlank
          @Size(max = 2048)
          @Pattern(
              regexp = "(?:https?://[^\\s]+|/(?!/)[^\\s]*)",
              message = "Use an HTTP(S) URL or a local absolute path.")
          String imageUrl,
      @NotNull @PositiveOrZero Integer sortOrder,
      @NotNull Boolean isCover) {}

  public record AmenityRequest(
      @NotBlank @Size(max = 120) String name, @Size(max = 100) String icon) {}

  public record AmenitySelection(@NotNull @Size(max = 100) Set<@NotNull UUID> amenityIds) {}
}
