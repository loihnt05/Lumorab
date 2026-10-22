package com.lumora.booking;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

public record BookingFilter(
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
    BookingStatus status,
    @Size(max = 64) String bookingCode,
    UUID roomId,
    @Min(0) Integer page,
    @Min(1) @Max(100) Integer size) {
  public BookingFilter {
    page = page == null ? 0 : page;
    size = size == null ? 20 : size;
    bookingCode = bookingCode == null || bookingCode.isBlank() ? null : bookingCode.trim();
  }
}
