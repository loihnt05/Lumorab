package com.lumora.availability;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

public record AvailabilityRequest(
    @NotNull UUID propertyId,
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
    @NotNull @Positive Integer guests) {
  @AssertTrue(message = "checkOut must be after checkIn.")
  public boolean isDateRangeValid() {
    return checkIn == null || checkOut == null || checkIn.isBefore(checkOut);
  }
}
