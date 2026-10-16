package com.lumora.property;

import java.util.UUID;

public record PropertySummary(UUID id, String name, String slug, String status) {
  public static PropertySummary from(Property property) {
    return new PropertySummary(
        property.getId(), property.getName(), property.getSlug(), property.getStatus());
  }
}
