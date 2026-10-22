package com.lumora.property;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

public record PropertyDetail(
    UUID id,
    String name,
    String slug,
    String description,
    String address,
    String city,
    BigDecimal latitude,
    BigDecimal longitude,
    LocalTime checkInTime,
    LocalTime checkOutTime,
    String status) {
  public static PropertyDetail from(Property p) {
    return new PropertyDetail(
        p.getId(),
        p.getName(),
        p.getSlug(),
        p.getDescription(),
        p.getAddress(),
        p.getCity(),
        p.getLatitude(),
        p.getLongitude(),
        p.getCheckInTime(),
        p.getCheckOutTime(),
        p.getStatus());
  }
}
