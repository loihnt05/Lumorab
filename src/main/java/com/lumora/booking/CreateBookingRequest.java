package com.lumora.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

public record CreateBookingRequest(
    @NotNull UUID propertyId,
    @NotNull UUID roomId,
    @NotNull LocalDate checkIn,
    @NotNull LocalDate checkOut,
    @NotNull @Positive Integer guests,
    @NotNull @Valid PrimaryGuest primaryGuest,
    @Size(max = 2000) String specialRequest) {
  @AssertTrue(message = "Use calendar dates with years 1–9999 and checkOut after checkIn.")
  public boolean isDateRangeValid() {
    return checkIn == null
        || checkOut == null
        || (checkIn.getYear() >= 1 && checkOut.getYear() <= 9999 && checkIn.isBefore(checkOut));
  }

  public record PrimaryGuest(
      @NotBlank @Size(max = 255) String fullName,
      @NotBlank @Size(max = 32) String phone,
      @NotBlank @Email @Size(max = 320) String email) {}
}
