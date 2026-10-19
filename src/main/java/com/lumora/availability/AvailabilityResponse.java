package com.lumora.availability;

import com.lumora.catalog.PublicViews;
import com.lumora.pricing.PriceQuote;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AvailabilityResponse(
    UUID propertyId,
    LocalDate checkIn,
    LocalDate checkOut,
    int guests,
    Instant checkedAt,
    List<Result> rooms) {
  public record Result(PublicViews.Room room, PriceQuote quote) {}
}
